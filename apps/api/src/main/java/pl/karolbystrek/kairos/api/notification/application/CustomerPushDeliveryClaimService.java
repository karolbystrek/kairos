package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.application.model.ClaimedCustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.PushSubscriptionCipher;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderOutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPushDeliveryClaimService {

    private static final String ENDPOINT_PURPOSE = "endpoint";
    private static final String AUTH_SECRET_PURPOSE = "auth-secret";

    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderOutboxEventRepository outboxRepository;
    private final PushSubscriptionCipher cipher;
    private final VapidKeyMaterial vapidKeyMaterial;
    private final CustomerPushSubscriptionRetirementService retirementService;
    private final CustomerNotificationProperties properties;
    private final Clock clock;

    @Transactional
    public List<ClaimedCustomerPushDelivery> claimAvailable() {
        var now = clock.instant();
        var claimed = new ArrayList<ClaimedCustomerPushDelivery>();
        for (var delivery : deliveryRepository.findAvailableForClaim(
                now,
                properties.worker().batchSize()
        )) {
            var claimToken = UUID.randomUUID();
            delivery.claim(claimToken, now, properties.worker().claimLease());
            if (!delivery.getDeadlineAt().isAfter(now)) {
                delivery.expire(claimToken, now, "Customer Push freshness deadline has passed");
                continue;
            }
            var event = outboxRepository.findById(delivery.getOutboxEventId()).orElse(null);
            var order = orderRepository.findById(delivery.getOrderId()).orElse(null);
            if (event == null || order == null || order.getStatus() != event.getStatus()) {
                delivery.retry(
                        claimToken,
                        now,
                        null,
                        "STALE_EVENT",
                        "Customer Push event no longer represents the authoritative order state"
                );
                delivery.supersede(now);
                continue;
            }
            var subscriptionId = delivery.getSubscriptionId();
            if (subscriptionId == null) {
                delivery.retry(
                        claimToken,
                        now,
                        null,
                        "SUBSCRIPTION_REMOVED",
                        "Customer Push subscription no longer exists"
                );
                delivery.cancel(now);
                continue;
            }
            var subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
            if (subscription == null) {
                delivery.retry(
                        claimToken,
                        now,
                        null,
                        "SUBSCRIPTION_REMOVED",
                        "Customer Push subscription no longer exists"
                );
                delivery.cancel(now);
                continue;
            }
            if (subscription.isExpiredAt(now)) {
                delivery.retry(
                        claimToken,
                        now,
                        null,
                        "SUBSCRIPTION_EXPIRED",
                        "Customer Push subscription has expired"
                );
                retirementService.retire(subscription.getId(), now);
                continue;
            }
            if (!subscription.getVapidKeyFingerprint().equals(vapidKeyMaterial.fingerprint())) {
                delivery.retry(
                        claimToken,
                        now,
                        null,
                        "VAPID_KEY_REPLACED",
                        "Customer Push subscription is restricted to a retired VAPID key"
                );
                retirementService.retire(subscription.getId(), now);
                continue;
            }
            try {
                var endpoint = new String(cipher.decrypt(
                        subscription.getEncryptedEndpoint(),
                        subscription.getEndpointNonce(),
                        subscription.getId(),
                        ENDPOINT_PURPOSE
                ), StandardCharsets.UTF_8);
                var authSecret = cipher.decrypt(
                        subscription.getEncryptedAuthSecret(),
                        subscription.getAuthSecretNonce(),
                        subscription.getId(),
                        AUTH_SECRET_PURPOSE
                );
                claimed.add(new ClaimedCustomerPushDelivery(
                        delivery.getId(),
                        claimToken,
                        subscription.getId(),
                        event.getId(),
                        endpoint,
                        subscription.getP256dhKey(),
                        authSecret,
                        delivery.getPayload(),
                        delivery.getDeadlineAt()
                ));
            } catch (RuntimeException exception) {
                delivery.deadLetter(
                        claimToken,
                        now,
                        null,
                        "SUBSCRIPTION_DATA_ERROR",
                        "Stored Customer Push subscription data could not be prepared"
                );
            }
        }
        return List.copyOf(claimed);
    }
}
