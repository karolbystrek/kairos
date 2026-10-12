package pl.karolbystrek.kairos.api.analytics.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.analytics.application.OrderAnalyticsService;

@Component
@RequiredArgsConstructor
@Slf4j
class OrderAnalyticsScheduler {
    private final MeterRegistry meters;
    private final OrderAnalyticsService analytics;

    @Scheduled(fixedDelayString = "${kairos.analytics.poll-delay:10s}",
            initialDelayString = "${kairos.analytics.initial-delay:1s}")
    void process() {
        var sample = Timer.start(meters);
        try {
            analytics.processAvailable();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "analytics").increment();
            log.error("Order analytics cycle failed; uncommitted measurements will be retried", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "analytics"));
        }
    }
}
