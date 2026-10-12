package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryClaimService;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryProcessor;

import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
class WebhookDeliveryScheduler {

    private final MeterRegistry meters;
    private final WebhookDeliveryClaimService claimService;
    private final WebhookDeliveryProcessor deliveryProcessor;

    @Scheduled(
            fixedDelayString = "${kairos.webhooks.worker.delivery-poll-delay:500ms}",
            initialDelayString = "${kairos.webhooks.worker.initial-delay:1s}"
    )
    void deliver() {
        var sample = Timer.start(meters);
        try {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        meters.counter("kairos.worker.failures", "worker", "webhook_delivery").increment();
                        log.error("Webhook delivery {} failed unexpectedly", delivery.id(), exception);
                    }
                }));
            }
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "webhook_delivery").increment();
            log.error("Webhook delivery cycle failed", exception);
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", "webhook_delivery"));
        }
    }
}
