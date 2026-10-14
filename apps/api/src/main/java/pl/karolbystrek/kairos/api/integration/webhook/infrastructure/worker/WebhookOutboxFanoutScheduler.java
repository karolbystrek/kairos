package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookOutboxFanoutService;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
@RequiredArgsConstructor
class WebhookOutboxFanoutScheduler {

    private final WorkerCycles cycles;
    private final WebhookOutboxFanoutService fanoutService;

    boolean fanOut() {
        return cycles.run("webhook_fanout", "Webhook outbox fan-out cycle failed",
                () -> fanoutService.fanOutAvailable() > 0);
    }
}
