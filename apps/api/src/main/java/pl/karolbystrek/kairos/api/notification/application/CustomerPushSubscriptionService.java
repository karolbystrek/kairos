package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.application.exception.CustomerPushEnrollmentLimitException;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;
import pl.karolbystrek.kairos.api.notification.application.model.ValidatedPushSubscription;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushEnrollment;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushSubscription;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
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
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAccessContext databaseAccessContext;

    @Transactional
    public void reconcile(
            CustomerPushSubscriptionInput input,
            Collection<UUID> trackingReferences
    ) {
        var validated = validator.validate(input);
        var id = verifiedSubscriptionId(validated);
        if (id == null) {
            id = createSubscriptionId(validated, clock.instant());
        }
        databaseAccessContext.pushCapability(List.of(id), trackingReferences);
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
        capabilities.sort(java.util.Comparator.comparing(ValidatedPushSubscription::endpointHash));
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
        if (currentId == null) {
            ids.add(createSubscriptionId(current, clock.instant()));
        }
        databaseAccessContext.pushCapability(ids, trackingReferences);
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
        if (existing == null) {
            throw new InvalidCustomerPushSubscriptionException("Push subscription is unavailable");
        }
        var subscription = refreshSubscription(existing, validated, now);
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
            if (jdbcTemplate.queryForObject("SELECT public.count_push_enrollments(?)", Long.class, order.getTrackingReference())
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
        return jdbcTemplate.query("SELECT * FROM public.verify_push_subscription(?)", rows -> {
            if (!rows.next()) {
                return null;
            }
            var id = rows.getObject("id", UUID.class);
            var auth = cipher.decrypt(rows.getBytes("encrypted_auth_secret"),
                    rows.getBytes("auth_secret_nonce"), id, AUTH_SECRET_PURPOSE);
            if (!MessageDigest.isEqual(rows.getBytes("p256dh_key"), candidate.p256dhKey())
                    || !MessageDigest.isEqual(auth, candidate.authSecret())) {
                throw new InvalidCustomerPushSubscriptionException(
                        "Push subscription capability does not match the registered endpoint");
            }
            return id;
        }, candidate.endpointHash());
    }

    private UUID createSubscriptionId(ValidatedPushSubscription validated, java.time.Instant now) {
        if (validated.expirationTime() != null && !validated.expirationTime().isAfter(now)) {
            throw new InvalidCustomerPushSubscriptionException("Push subscription has already expired");
        }
        var id = UUID.randomUUID();
        var endpoint = cipher.encrypt(validated.endpoint().getBytes(StandardCharsets.UTF_8), id, ENDPOINT_PURPOSE);
        var auth = cipher.encrypt(validated.authSecret(), id, AUTH_SECRET_PURPOSE);
        var created = jdbcTemplate.queryForObject(
                "SELECT public.create_push_subscription(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.class,
                id, validated.endpointHash(), validated.endpointOrigin(), endpoint.ciphertext(), endpoint.nonce(),
                validated.p256dhKey(), auth.ciphertext(), auth.nonce(), vapidKeyMaterial.fingerprint(),
                validated.expirationTime() == null ? null : Timestamp.from(validated.expirationTime()), Timestamp.from(now));
        if (created != null) {
            return created;
        }
        var existing = verifiedSubscriptionId(validated);
        if (existing == null) {
            throw new InvalidCustomerPushSubscriptionException("Push subscription changed concurrently; retry the request");
        }
        return existing;
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
        jdbcTemplate.queryForObject("SELECT public.cancel_push_deliveries(?, ?::uuid[], ?)", Integer.class,
                subscriptionId, orderIds == null ? null : orderIds.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}")),
                Timestamp.from(clock.instant()));
    }
}
