package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.application.exception.CustomerPushEnrollmentLimitException;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;
import pl.karolbystrek.kairos.api.notification.application.model.ValidatedPushSubscription;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushEnrollment;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushSubscription;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushEnrollmentRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.PushSubscriptionCipher;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerPushSubscriptionService {

    private static final String ENDPOINT_PURPOSE = "endpoint";
    private static final String AUTH_SECRET_PURPOSE = "auth-secret";

    private final CustomerPushSubscriptionValidator validator;
    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushEnrollmentRepository enrollmentRepository;
    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerOrderRepository orderRepository;
    private final PushSubscriptionCipher cipher;
    private final VapidKeyMaterial vapidKeyMaterial;
    private final CustomerNotificationProperties properties;
    private final Clock clock;

    @Transactional
    public void reconcile(
            CustomerPushSubscriptionInput input,
            Collection<UUID> trackingReferences
    ) {
        reconcileValidated(validator.validate(input), trackingReferences);
    }

    @Transactional
    public void replace(
            CustomerPushSubscriptionInput previousInput,
            CustomerPushSubscriptionInput currentInput,
            Collection<UUID> trackingReferences
    ) {
        removeSubscription(validator.validate(previousInput));
        reconcileValidated(validator.validate(currentInput), trackingReferences);
    }

    @Transactional
    public void disable(CustomerPushSubscriptionInput input) {
        removeSubscription(validator.validate(input));
    }

    @Transactional
    public void removeEnrollments(
            CustomerPushSubscriptionInput input,
            Collection<UUID> trackingReferences
    ) {
        var validated = validator.validate(input);
        var subscription = subscriptionRepository
                .findForUpdateByEndpointHash(validated.endpointHash())
                .orElse(null);
        if (subscription == null) {
            return;
        }
        requireMatchingCapability(subscription, validated);
        var orderIds = trackingReferences.stream()
                .distinct()
                .map(orderRepository::findByTrackingReference)
                .flatMap(java.util.Optional::stream)
                .map(CustomerOrder::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return;
        }
        cancelPending(subscription.getId(), orderIds);
        enrollmentRepository.deleteAllBySubscriptionIdAndOrderIdIn(
                subscription.getId(),
                orderIds
        );
        subscription.checkIn(clock.instant());
    }

    private void reconcileValidated(
            ValidatedPushSubscription validated,
            Collection<UUID> trackingReferences
    ) {
        var now = clock.instant();
        if (validated.expirationTime() != null
                && !validated.expirationTime().isAfter(now)) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription has already expired"
            );
        }
        var existing = subscriptionRepository
                .findForUpdateByEndpointHash(validated.endpointHash())
                .orElse(null);
        if (existing != null) {
            requireMatchingCapability(existing, validated);
        }
        var subscription = existing == null
                ? createSubscription(validated, now)
                : refreshSubscription(existing, validated, now);
        var desiredOrdersById = trackingReferences.stream()
                .distinct()
                .sorted()
                .map(orderRepository::findForUpdateByTrackingReference)
                .flatMap(java.util.Optional::stream)
                .filter(order -> order.getStatus().isActive())
                .collect(Collectors.toMap(
                        CustomerOrder::getId,
                        order -> order,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        var existingEnrollments = enrollmentRepository.findAllBySubscriptionId(
                subscription.getId()
        );
        var obsoleteOrderIds = existingEnrollments.stream()
                .map(CustomerPushEnrollment::getOrderId)
                .filter(orderId -> !desiredOrdersById.containsKey(orderId))
                .toList();
        if (!obsoleteOrderIds.isEmpty()) {
            cancelPending(subscription.getId(), obsoleteOrderIds);
            enrollmentRepository.deleteAllBySubscriptionIdAndOrderIdIn(
                    subscription.getId(),
                    obsoleteOrderIds
            );
        }
        var existingOrderIds = existingEnrollments.stream()
                .map(CustomerPushEnrollment::getOrderId)
                .collect(Collectors.toSet());
        for (var order : desiredOrdersById.values()) {
            if (existingOrderIds.contains(order.getId())) {
                continue;
            }
            if (enrollmentRepository.countByOrderId(order.getId())
                    >= properties.subscription().maximumEnrollmentsPerOrder()) {
                throw new CustomerPushEnrollmentLimitException(order.getTrackingReference());
            }
            enrollmentRepository.save(CustomerPushEnrollment.create(
                    subscription.getId(),
                    order.getId(),
                    now
            ));
        }
    }

    private CustomerPushSubscription createSubscription(
            ValidatedPushSubscription validated,
            java.time.Instant now
    ) {
        var subscriptionId = UUID.randomUUID();
        var encryptedEndpoint = cipher.encrypt(
                validated.endpoint().getBytes(StandardCharsets.UTF_8),
                subscriptionId,
                ENDPOINT_PURPOSE
        );
        var encryptedAuth = cipher.encrypt(
                validated.authSecret(),
                subscriptionId,
                AUTH_SECRET_PURPOSE
        );
        return subscriptionRepository.saveAndFlush(CustomerPushSubscription.create(
                subscriptionId,
                validated.endpointHash(),
                validated.endpointOrigin(),
                encryptedEndpoint.ciphertext(),
                encryptedEndpoint.nonce(),
                validated.p256dhKey(),
                encryptedAuth.ciphertext(),
                encryptedAuth.nonce(),
                vapidKeyMaterial.fingerprint(),
                validated.expirationTime(),
                now
        ));
    }

    private CustomerPushSubscription refreshSubscription(
            CustomerPushSubscription subscription,
            ValidatedPushSubscription validated,
            java.time.Instant now
    ) {
        var encryptedEndpoint = cipher.encrypt(
                validated.endpoint().getBytes(StandardCharsets.UTF_8),
                subscription.getId(),
                ENDPOINT_PURPOSE
        );
        var encryptedAuth = cipher.encrypt(
                validated.authSecret(),
                subscription.getId(),
                AUTH_SECRET_PURPOSE
        );
        subscription.refresh(
                validated.endpointOrigin(),
                encryptedEndpoint.ciphertext(),
                encryptedEndpoint.nonce(),
                validated.p256dhKey(),
                encryptedAuth.ciphertext(),
                encryptedAuth.nonce(),
                vapidKeyMaterial.fingerprint(),
                validated.expirationTime(),
                now
        );
        return subscription;
    }

    private void removeSubscription(ValidatedPushSubscription validated) {
        var subscription = subscriptionRepository
                .findForUpdateByEndpointHash(validated.endpointHash())
                .orElse(null);
        if (subscription == null) {
            return;
        }
        requireMatchingCapability(subscription, validated);
        cancelPending(subscription.getId(), null);
        enrollmentRepository.deleteAllBySubscriptionId(subscription.getId());
        subscriptionRepository.delete(subscription);
    }

    private void requireMatchingCapability(
            CustomerPushSubscription subscription,
            ValidatedPushSubscription candidate
    ) {
        var storedAuth = cipher.decrypt(
                subscription.getEncryptedAuthSecret(),
                subscription.getAuthSecretNonce(),
                subscription.getId(),
                AUTH_SECRET_PURPOSE
        );
        if (!MessageDigest.isEqual(subscription.getP256dhKey(), candidate.p256dhKey())
                || !MessageDigest.isEqual(storedAuth, candidate.authSecret())) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription capability does not match the registered endpoint"
            );
        }
    }

    private void cancelPending(UUID subscriptionId, List<UUID> orderIds) {
        var pending = orderIds == null
                ? deliveryRepository.findAllBySubscriptionIdAndStatus(
                subscriptionId,
                CustomerPushDeliveryStatus.PENDING
        )
                : deliveryRepository.findAllBySubscriptionIdAndOrderIdInAndStatus(
                subscriptionId,
                orderIds,
                CustomerPushDeliveryStatus.PENDING
        );
        var now = clock.instant();
        pending.forEach(delivery -> delivery.cancel(now));
    }
}
