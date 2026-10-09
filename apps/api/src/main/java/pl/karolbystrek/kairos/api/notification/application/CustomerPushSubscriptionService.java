package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.application.exception.CustomerPushEnrollmentLimitException;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;
import pl.karolbystrek.kairos.api.notification.application.model.ValidatedPushSubscription;
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
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
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
    private final CustomerOrderRepository orderRepository;
    private final PushSubscriptionCipher cipher;
    private final VapidKeyMaterial vapidKeyMaterial;
    private final CustomerNotificationProperties properties;
    private final Clock clock;
    private final CustomerPushDeliveryRepository deliveryRepository;
    private final DatabaseAccessContext databaseAccessContext;

    @Transactional
    public void reconcile(
            CustomerPushSubscriptionInput input,
            Collection<UUID> trackingReferences
    ) {
        var validated = validator.validate(input);
        var id = verifiedSubscriptionId(validated);
        var created = id == null ? createSubscription(validated, clock.instant()) : null;
        databaseAccessContext.pushCapability(List.of(created == null ? id : created.getId()), trackingReferences);
        if (created != null) {
            subscriptionRepository.saveAndFlush(created);
        }
        reconcileValidated(validated, trackingReferences);
    }

    @Transactional
    public void replace(
            CustomerPushSubscriptionInput previousInput,
            CustomerPushSubscriptionInput currentInput,
            Collection<UUID> trackingReferences
    ) {
        var previous = validator.validate(previousInput);
        var current = validator.validate(currentInput);
        // Lock endpoint capabilities in a stable order before installing the immutable union.
        var capabilities = new ArrayList<>(List.of(previous, current));
        capabilities.sort(Comparator.comparing(ValidatedPushSubscription::endpointHash));
        var ids = new ArrayList<UUID>();
        UUID currentId = null;
        for (var capability : capabilities) {
            var id = verifiedSubscriptionId(capability);
            if (id != null) {
                ids.add(id);
                if (capability.endpointHash().equals(current.endpointHash())) {
                    currentId = id;
                }
            }
        }
        var created = currentId == null ? createSubscription(current, clock.instant()) : null;
        if (created != null) {
            ids.add(created.getId());
        }
        databaseAccessContext.pushCapability(ids, trackingReferences);
        if (created != null) {
            subscriptionRepository.saveAndFlush(created);
        }
        if (!previous.endpointHash().equals(current.endpointHash())) {
            removeSubscription(previous);
        }
        reconcileValidated(current, trackingReferences);
    }

    @Transactional
    public void disable(CustomerPushSubscriptionInput input) {
        var validated = validator.validate(input);
        var id = verifiedSubscriptionId(validated);
        if (id == null) {
            return;
        }
        databaseAccessContext.pushCapability(List.of(id), List.of());
        removeSubscription(validated);
    }

    @Transactional
    public void removeEnrollments(
            CustomerPushSubscriptionInput input,
            Collection<UUID> trackingReferences
    ) {
        var validated = validator.validate(input);
        var id = verifiedSubscriptionId(validated);
        if (id == null) {
            return;
        }
        databaseAccessContext.pushCapability(List.of(id), trackingReferences);
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
                .flatMap(Optional::stream)
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
        if (existing == null) {
            throw new InvalidCustomerPushSubscriptionException("Push subscription is unavailable");
        }
        var subscription = refreshSubscription(existing, validated, now);
        var desiredOrdersById = trackingReferences.stream()
                .distinct()
                .sorted()
                .map(orderRepository::findForUpdateByTrackingReference)
                .flatMap(Optional::stream)
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
            if (enrollmentRepository.countForTrackingReference(order.getTrackingReference())
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

    private UUID verifiedSubscriptionId(ValidatedPushSubscription candidate) {
        var capability = subscriptionRepository.verifyCapability(candidate.endpointHash()).orElse(null);
        if (capability == null) {
            return null;
        }
        var auth = cipher.decrypt(capability.getEncryptedAuthSecret(),
                capability.getAuthSecretNonce(), capability.getId(), AUTH_SECRET_PURPOSE);
        if (!MessageDigest.isEqual(capability.getP256dhKey(), candidate.p256dhKey())
                || !MessageDigest.isEqual(auth, candidate.authSecret())) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription capability does not match the registered endpoint");
        }
        return capability.getId();
    }

    private CustomerPushSubscription createSubscription(ValidatedPushSubscription validated, Instant now) {
        if (validated.expirationTime() != null && !validated.expirationTime().isAfter(now)) {
            throw new InvalidCustomerPushSubscriptionException("Push subscription has already expired");
        }
        var id = UUID.randomUUID();
        var endpoint = cipher.encrypt(validated.endpoint().getBytes(StandardCharsets.UTF_8), id, ENDPOINT_PURPOSE);
        var auth = cipher.encrypt(validated.authSecret(), id, AUTH_SECRET_PURPOSE);
        return CustomerPushSubscription.create(
                id, validated.endpointHash(), validated.endpointOrigin(), endpoint.ciphertext(), endpoint.nonce(),
                validated.p256dhKey(), auth.ciphertext(), auth.nonce(), vapidKeyMaterial.fingerprint(),
                validated.expirationTime(), now);
    }

    private CustomerPushSubscription refreshSubscription(
            CustomerPushSubscription subscription,
            ValidatedPushSubscription validated,
            Instant now
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
        deliveryRepository.cancelPending(subscriptionId, orderIds, clock.instant());
    }
}
