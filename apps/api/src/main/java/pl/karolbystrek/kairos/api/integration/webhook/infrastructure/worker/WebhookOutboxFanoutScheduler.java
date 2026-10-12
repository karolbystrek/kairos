package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookOutboxFanoutService;

@Component
@RequiredArgsConstructor
@Slf4j
class WebhookOutboxFanoutScheduler {

    private final MeterRegistry meters;
    private final WebhookOutboxFanoutService fanoutService;

    @Scheduled(
            fixedDelayString = "${kairos.webhooks.worker.fanout-poll-delay:500ms}",
            initialDelayString = "${kairos.webhooks.worker.initial-delay:1s}"
    )
    void fanOut() {
        var sample = Timer.start(meters);
        try {
            fanoutService.fanOutAvailable();
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "webhook_fanout").increment();
            log.error("Webhook outbox fan-out cycle failed", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "webhook_fanout"));
        }
    }
}
