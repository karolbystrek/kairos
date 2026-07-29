package pl.karolbystrek.kairos.api.notification.application.model;

import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ClaimedCustomerPushDelivery(
        @NonNull UUID id,
        @NonNull UUID claimToken,
        @NonNull UUID subscriptionId,
        @NonNull UUID eventId,
        @NonNull String endpoint,
        byte @NonNull [] p256dhKey,
        byte @NonNull [] authSecret,
        @NonNull String payload,
        @NonNull Instant deadline
) {

    public ClaimedCustomerPushDelivery {
        p256dhKey = p256dhKey.clone();
        authSecret = authSecret.clone();
    }

    @Override
    public byte[] p256dhKey() {
        return p256dhKey.clone();
    }

    @Override
    public byte[] authSecret() {
        return authSecret.clone();
    }
}
