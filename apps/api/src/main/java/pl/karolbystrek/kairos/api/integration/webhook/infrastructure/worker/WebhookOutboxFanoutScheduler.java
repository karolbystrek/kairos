package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookOutboxFanoutService;

@Component
@ConditionalOnProperty(name = "kairos.runtime-mode", havingValue = "worker")
@RequiredArgsConstructor
@Slf4j
class WebhookOutboxFanoutScheduler {

    private final WebhookOutboxFanoutService fanoutService;

    @Scheduled(
            fixedDelayString = "${kairos.webhooks.worker.fanout-poll-delay:500ms}",
            initialDelayString = "${kairos.webhooks.worker.initial-delay:1s}"
    )
    void fanOut() {
        try {
            fanoutService.fanOutAvailable();
        } catch (RuntimeException exception) {
            log.error("Webhook outbox fan-out cycle failed", exception);
        }
    }
}
