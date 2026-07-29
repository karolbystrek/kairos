package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CustomerPushCleanupService {

    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerPushSubscriptionRetirementService retirementService;
    private final CustomerNotificationProperties properties;
    private final Clock clock;

    @Transactional
    public int clean() {
        var now = clock.instant();
        var dormant = subscriptionRepository.findExpiredOrDormantForCleanup(
                now,
                now.minus(properties.subscription().dormantRetention()),
                properties.worker().batchSize()
        );
        dormant.forEach(subscription -> retirementService.retire(subscription.getId(), now));
        var deliveries = deliveryRepository.findTerminalForCleanup(
                now.minus(properties.delivery().successfulRetention()),
                now.minus(properties.delivery().failedRetention()),
                properties.worker().batchSize()
        );
        deliveryRepository.deleteAllInBatch(deliveries);
        return dormant.size() + deliveries.size();
    }
}
