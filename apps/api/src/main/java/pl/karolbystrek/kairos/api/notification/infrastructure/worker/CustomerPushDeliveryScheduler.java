package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryClaimService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryProcessor;

import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushDeliveryScheduler {

    private final MeterRegistry meters;
    private final CustomerPushDeliveryClaimService claimService;
    private final CustomerPushDeliveryProcessor deliveryProcessor;

    boolean deliver() {
        var sample = Timer.start(meters);
        try {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        meters.counter("kairos.worker.failures", "worker", "push_delivery").increment();
                        log.error(
                                "Customer Push delivery {} failed unexpectedly",
                                delivery.id(),
                                exception
                        );
                    }
                }));
            }
            return !claimed.isEmpty();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "push_delivery").increment();
            log.error("Customer Push delivery cycle failed", exception);
            return false;
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "push_delivery"));
        }
    }
}
