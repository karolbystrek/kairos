package pl.karolbystrek.kairos.api.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.OrderMeasurementRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;

@Service
@RequiredArgsConstructor
public class OrderAnalyticsService {
    private final OrderMeasurementRepository measurements;
    private final WorkerDiscoveryRepository discovery;
    private final DatabaseAccessContext accessContext;
    private final PlatformTransactionManager transactionManager;

    public int processAvailable() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var count = 0;
        // Drains the backlog in one cycle; each measurement commits on its own.
        while (true) {
            var processed = transaction.execute(status -> {
                var id = discovery.nextOrderMeasurement();
                if (id == null || !accessContext.worker(WorkerOperation.ANALYTICS, id, null)) {
                    return false;
                }
                measurements.aggregateAndConsume(id);
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
