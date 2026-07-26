package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryClaimService;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryProcessor;

import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "kairos.runtime-mode", havingValue = "worker")
@RequiredArgsConstructor
@Slf4j
class WebhookDeliveryScheduler {

    private final WebhookDeliveryClaimService claimService;
    private final WebhookDeliveryProcessor deliveryProcessor;

    @Scheduled(
            fixedDelayString = "${kairos.webhooks.worker.delivery-poll-delay:500ms}",
            initialDelayString = "${kairos.webhooks.worker.initial-delay:1s}"
    )
    void deliver() {
        try {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        log.error("Webhook delivery {} failed unexpectedly", delivery.id(), exception);
                    }
                }));
            }
        } catch (RuntimeException exception) {
            log.error("Webhook delivery cycle failed", exception);
        }
    }
}
