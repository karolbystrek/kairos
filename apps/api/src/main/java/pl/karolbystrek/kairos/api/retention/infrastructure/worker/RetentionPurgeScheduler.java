package pl.karolbystrek.kairos.api.retention.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.retention.application.RetentionPurgeService;

@Component
@RequiredArgsConstructor
@Slf4j
class RetentionPurgeScheduler {

    private final RetentionPurgeService purgeService;

    @Scheduled(
            fixedDelayString = "${kairos.retention.purge-delay:1h}",
            initialDelayString = "${kairos.retention.purge-initial-delay:10m}"
    )
    void purge() {
        try {
            purgeService.purge();
        } catch (RuntimeException exception) {
            log.error("Retention purge cycle failed", exception);
        }
    }
}
