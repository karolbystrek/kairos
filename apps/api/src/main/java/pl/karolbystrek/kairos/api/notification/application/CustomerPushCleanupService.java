package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CustomerPushCleanupService {

    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerPushSubscriptionRetirementService retirementService;
    private final CustomerNotificationProperties properties;
    private final Clock clock;
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseAccessContext databaseAccessContext;

    public int clean() {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var now = clock.instant();
        var count = 0;
        for (var index = 0; index < properties.worker().batchSize(); index++) {
            var processed = transactionTemplate.execute(status -> {
                var id = workerDiscoveryRepository.nextExpiredPushSubscription(now);
                if (id == null) {
                    id = workerDiscoveryRepository.nextDormantPushSubscription(
                            now.minus(properties.subscription().dormantRetention()));
                }
                if (id == null || !databaseAccessContext.worker(WorkerOperation.SUBSCRIPTION_RETIRE, id, null)) {
                    return false;
                }
                retirementService.retire(id, now);
                return true;
            });
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
            count++;
        }
        for (var index = 0; index < properties.worker().batchSize(); index++) {
            var processed = transactionTemplate.execute(status -> {
                var id = workerDiscoveryRepository.nextTerminalPushDelivery(
                        now.minus(properties.delivery().successfulRetention()),
                        now.minus(properties.delivery().failedRetention()));
                if (id == null || !databaseAccessContext.worker(WorkerOperation.PUSH_CLEANUP, id, null)) {
                    return false;
                }
                deliveryRepository.deleteById(id);
                return true;
            });
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
            count++;
        }
        return count;
    }
}
