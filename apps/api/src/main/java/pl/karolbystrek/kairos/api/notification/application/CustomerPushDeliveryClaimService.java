package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;

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
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseAccessContext databaseAccessContext;

    public List<ClaimedCustomerPushDelivery> claimAvailable() {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var claimed = new ArrayList<ClaimedCustomerPushDelivery>();
        for (var index = 0; index < properties.worker().batchSize(); index++) {
            var processed = transactionTemplate.execute(status -> claimOne(claimed));
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
        }
        return List.copyOf(claimed);
    }

    private boolean claimOne(List<ClaimedCustomerPushDelivery> claimed) {
        var now = clock.instant();
        var id = workerDiscoveryRepository.nextPushDelivery(now);
        if (id == null || !databaseAccessContext.worker(WorkerOperation.PUSH_CLAIM, id, null)) {
            return false;
        }
        var delivery = deliveryRepository.findById(id).orElseThrow();
        var claimToken = UUID.randomUUID();
        delivery.claim(claimToken, now, properties.worker().claimLease());
        if (!delivery.getDeadlineAt().isAfter(now)) {
            delivery.expire(claimToken, now, "Customer Push freshness deadline has passed");
            return true;
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
            return true;
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
            return true;
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
            return true;
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
            return true;
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
            return true;
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
        return true;
    }
}
