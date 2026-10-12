package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushCleanupService;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushCleanupScheduler {

    private final MeterRegistry meters;
    private final CustomerPushCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.cleanup-delay:1h}",
            initialDelayString = "${kairos.customer-notifications.worker.cleanup-initial-delay:5m}"
    )
    void clean() {
        var sample = Timer.start(meters);
        try {
            cleanupService.clean();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "push_cleanup").increment();
            log.error("Customer Push cleanup cycle failed", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "push_cleanup"));
        }
    }
}
