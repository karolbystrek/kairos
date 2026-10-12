package pl.karolbystrek.kairos.api.retention.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.retention.application.RetentionPurgeService;

@Component
@RequiredArgsConstructor
@Slf4j
class RetentionPurgeScheduler {

    private final MeterRegistry meters;
    private final RetentionPurgeService purgeService;

    @Scheduled(
            fixedDelayString = "${kairos.retention.purge-delay:1h}",
            initialDelayString = "${kairos.retention.purge-initial-delay:10m}"
    )
    void purge() {
        var sample = Timer.start(meters);
        try {
            purgeService.purge();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "retention").increment();
            log.error("Retention purge cycle failed", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "retention"));
        }
    }
}
