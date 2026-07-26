package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RotateWebhookSigningSecretRequest(
        @NotNull UUID subscriptionId
) {
}
