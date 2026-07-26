package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;

public record IssuedWebhookSigningSecretView(
        @NonNull WebhookSigningSecretVersionView version,
        @NonNull String signingSecret
) {
}
