package pl.karolbystrek.kairos.api.analytics.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.analytics.application.OrderAnalyticsService;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
@RequiredArgsConstructor
class OrderAnalyticsScheduler {
    // Runs off the scheduler threads so a slow cycle cannot delay stream heartbeats.
    private final CoalescingWorker worker = new CoalescingWorker(this::cycle);
    private final WorkerCycles cycles;
    private final OrderAnalyticsService analytics;

    @Scheduled(fixedDelayString = "${kairos.analytics.poll-delay:10s}",
            initialDelayString = "${kairos.analytics.initial-delay:1s}")
    void process() {
        worker.wake();
    }

    private boolean cycle() {
        cycles.run("analytics", "Order analytics cycle failed; uncommitted measurements will be retried",
                analytics::processAvailable);
        return false;
    }
}
