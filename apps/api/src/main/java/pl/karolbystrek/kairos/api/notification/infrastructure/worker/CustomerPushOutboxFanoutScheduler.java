package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushOutboxFanoutService;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushOutboxFanoutScheduler {

    private final MeterRegistry meters;
    private final CustomerPushOutboxFanoutService fanoutService;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.fanout-poll-delay:500ms}",
            initialDelayString = "${kairos.customer-notifications.worker.initial-delay:1s}"
    )
    void fanOut() {
        var sample = Timer.start(meters);
        try {
            fanoutService.fanOutAvailable();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "push_fanout").increment();
            log.error("Customer Push outbox fan-out cycle failed", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "push_fanout"));
        }
    }
}
