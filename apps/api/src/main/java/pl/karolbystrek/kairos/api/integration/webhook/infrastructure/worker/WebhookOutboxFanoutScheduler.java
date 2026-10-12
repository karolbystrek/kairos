package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookOutboxFanoutService;

@Component
@RequiredArgsConstructor
@Slf4j
class WebhookOutboxFanoutScheduler {

    private final MeterRegistry meters;
    private final WebhookOutboxFanoutService fanoutService;

    boolean fanOut() {
        var sample = Timer.start(meters);
        try {
            return fanoutService.fanOutAvailable() > 0;
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "webhook_fanout").increment();
            log.error("Webhook outbox fan-out cycle failed", exception);
            return false;
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "webhook_fanout"));
        }
    }
}
