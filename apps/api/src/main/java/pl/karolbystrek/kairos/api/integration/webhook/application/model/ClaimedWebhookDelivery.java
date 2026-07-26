package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;

import java.util.List;
import java.util.UUID;

public record ClaimedWebhookDelivery(
        @NonNull UUID id,
        @NonNull UUID claimToken,
        @NonNull UUID subscriptionId,
        @NonNull String destinationUrl,
        @NonNull String payload,
        @NonNull List<UUID> signingSecretVersionIds
) {

    public ClaimedWebhookDelivery {
        signingSecretVersionIds = List.copyOf(signingSecretVersionIds);
    }
}
