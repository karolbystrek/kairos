package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;

public record IssuedWebhookSubscriptionView(
        @NonNull WebhookSubscriptionView subscription,
        @NonNull String signingSecret
) {
}
