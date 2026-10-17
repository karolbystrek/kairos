package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryClaimService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryProcessor;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.worker.DeliveryPool;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
class CustomerPushDeliveryScheduler {

    private final WorkerCycles cycles;
    private final CustomerPushDeliveryClaimService claimService;
    private final CustomerPushDeliveryProcessor deliveryProcessor;
    private final DeliveryPool pool;

    CustomerPushDeliveryScheduler(WorkerCycles cycles, CustomerPushDeliveryClaimService claimService,
                                  CustomerPushDeliveryProcessor deliveryProcessor,
                                  CustomerNotificationProperties properties) {
        this.cycles = cycles;
        this.claimService = claimService;
        this.deliveryProcessor = deliveryProcessor;
        pool = new DeliveryPool(properties.worker().batchSize());
    }

    boolean deliver() {
        return cycles.run("push_delivery", "Customer Push delivery cycle failed", () ->
                pool.dispatch(claimService::claimAvailable, delivery -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        cycles.recordFailure("push_delivery",
                                "Customer Push delivery " + delivery.id() + " failed unexpectedly", exception);
                    }
                }));
    }
}
