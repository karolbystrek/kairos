package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;

public record UpdateWebhookSubscriptionStatusRequest(
        @NotNull WebhookSubscriptionStatus status
) {
}
