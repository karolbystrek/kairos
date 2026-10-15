package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryClaimService;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryProcessor;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
class WebhookDeliveryScheduler {

    private final WorkerCycles cycles;
    private final WebhookDeliveryClaimService claimService;
    private final WebhookDeliveryProcessor deliveryProcessor;

    boolean deliver() {
        return cycles.run("webhook_delivery", "Webhook delivery cycle failed", () -> {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        cycles.recordFailure("webhook_delivery",
                                "Webhook delivery " + delivery.id() + " failed unexpectedly", exception);
                    }
                }));
            }
            return !claimed.isEmpty();
        });
    }
}
