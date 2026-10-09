package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ClaimedWebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpClient;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpResult;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security.WebhookSignatureService;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryProcessor {

    private final WebhookSignatureService signatureService;
    private final WebhookHttpClient httpClient;
    private final WebhookDeliveryCompletionService completionService;
    private final Clock clock;

    public void process(ClaimedWebhookDelivery delivery) {
        var attemptedAt = clock.instant();
        WebhookHttpResult result;
        try {
            var signatureHeader = signatureService.createHeader(
                    attemptedAt,
                    delivery.payload(),
                    delivery.signingSecrets()
            );
            result = httpClient.post(
                    delivery.destinationUrl(),
                    delivery.payload(),
                    signatureHeader
            );
        } catch (RuntimeException exception) {
            log.error("Could not prepare webhook delivery {}", delivery.id(), exception);
            result = new WebhookHttpResult(
                    null,
                    null,
                    false,
                    "SIGNING_ERROR",
                    "Webhook signing material could not be prepared"
            );
        }

        var completed = completionService.complete(
                delivery.id(),
                delivery.claimToken(),
                attemptedAt,
                clock.instant(),
                result
        );
        if (!completed) {
            log.warn("Discarded stale webhook delivery result for {}", delivery.id());
        }
    }
}
