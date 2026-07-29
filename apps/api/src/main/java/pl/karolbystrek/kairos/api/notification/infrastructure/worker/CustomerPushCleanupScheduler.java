package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushCleanupService;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushCleanupScheduler {

    private final CustomerPushCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.cleanup-delay:1h}",
            initialDelayString = "${kairos.customer-notifications.worker.cleanup-initial-delay:5m}"
    )
    void clean() {
        try {
            cleanupService.clean();
        } catch (RuntimeException exception) {
            log.error("Customer Push cleanup cycle failed", exception);
        }
    }
}
