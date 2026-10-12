package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushOutboxFanoutService;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushOutboxFanoutScheduler {

    private final MeterRegistry meters;
    private final CustomerPushOutboxFanoutService fanoutService;

    boolean fanOut() {
        var sample = Timer.start(meters);
        try {
            return fanoutService.fanOutAvailable() > 0;
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "push_fanout").increment();
            log.error("Customer Push outbox fan-out cycle failed", exception);
            return false;
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "push_fanout"));
        }
    }
}
