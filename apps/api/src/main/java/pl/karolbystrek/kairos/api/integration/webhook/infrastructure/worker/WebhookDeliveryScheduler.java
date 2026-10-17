package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryClaimService;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryProcessor;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.worker.DeliveryPool;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

import java.time.Duration;

@Component
class WebhookDeliveryScheduler {

    private final WorkerCycles cycles;
    private final WebhookDeliveryClaimService claimService;
    private final WebhookDeliveryProcessor deliveryProcessor;
    private final DeliveryPool pool;

    WebhookDeliveryScheduler(WorkerCycles cycles, WebhookDeliveryClaimService claimService,
                             WebhookDeliveryProcessor deliveryProcessor, WebhookProperties properties) {
        this.cycles = cycles;
        this.claimService = claimService;
        this.deliveryProcessor = deliveryProcessor;
        pool = new DeliveryPool(properties.worker().batchSize());
    }

    boolean deliver() {
        return cycles.run("webhook_delivery", "Webhook delivery cycle failed", () ->
                pool.dispatch(claimService::claimAvailable, delivery -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        cycles.recordFailure("webhook_delivery",
                                "Webhook delivery " + delivery.id() + " failed unexpectedly", exception);
                    }
                }));
    }

    // Runs before the data source closes, which the processor depends on.
    @PreDestroy
    void drain() throws InterruptedException {
        pool.awaitIdle(Duration.ofSeconds(15));
    }
}
