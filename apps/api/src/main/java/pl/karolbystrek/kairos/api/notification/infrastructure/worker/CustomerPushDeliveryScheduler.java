package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryClaimService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryProcessor;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
class CustomerPushDeliveryScheduler {

    private final WorkerCycles cycles;
    private final CustomerPushDeliveryClaimService claimService;
    private final CustomerPushDeliveryProcessor deliveryProcessor;

    boolean deliver() {
        return cycles.run("push_delivery", "Customer Push delivery cycle failed", () -> {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        cycles.recordFailure("push_delivery",
                                "Customer Push delivery " + delivery.id() + " failed unexpectedly", exception);
                    }
                }));
            }
            return !claimed.isEmpty();
        });
    }
}
