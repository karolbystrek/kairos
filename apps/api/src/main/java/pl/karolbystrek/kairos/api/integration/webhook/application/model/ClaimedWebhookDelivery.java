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
        @NonNull List<byte[]> signingSecrets
) {

    @Override
    public String toString() {
        return "ClaimedWebhookDelivery[id=" + id + ", claimToken=" + claimToken + "]";
    }

    public ClaimedWebhookDelivery {
        signingSecrets = signingSecrets.stream().map(byte[]::clone).toList();
    }
}
