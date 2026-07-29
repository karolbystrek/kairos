package pl.karolbystrek.kairos.api.notification.application.model;

import lombok.NonNull;

import java.time.Instant;

public record ValidatedPushSubscription(
        @NonNull String endpoint,
        @NonNull String endpointHash,
        @NonNull String endpointOrigin,
        byte @NonNull [] p256dhKey,
        byte @NonNull [] authSecret,
        Instant expirationTime
) {

    public ValidatedPushSubscription {
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
