package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushCleanupService;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
@RequiredArgsConstructor
class CustomerPushCleanupScheduler {

    private final WorkerCycles cycles;
    private final CustomerPushCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.cleanup-delay:1h}",
            initialDelayString = "${kairos.customer-notifications.worker.cleanup-initial-delay:5m}"
    )
    void clean() {
        cycles.run("push_cleanup", "Customer Push cleanup cycle failed", cleanupService::clean);
    }
}
