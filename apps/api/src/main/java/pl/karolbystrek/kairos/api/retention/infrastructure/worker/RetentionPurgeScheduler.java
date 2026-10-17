package pl.karolbystrek.kairos.api.retention.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.retention.application.RetentionPurgeService;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
@RequiredArgsConstructor
class RetentionPurgeScheduler {

    // Runs off the scheduler threads so a slow cycle cannot delay stream heartbeats.
    private final CoalescingWorker worker = new CoalescingWorker(this::cycle);

    private final WorkerCycles cycles;
    private final RetentionPurgeService purgeService;

    @Scheduled(
            fixedDelayString = "${kairos.retention.purge-delay:1h}",
            initialDelayString = "${kairos.retention.purge-initial-delay:10m}"
    )
    void purge() {
        worker.wake();
    }

    private boolean cycle() {
        cycles.run("retention", "Retention purge cycle failed", purgeService::purge);
        return false;
    }
}
