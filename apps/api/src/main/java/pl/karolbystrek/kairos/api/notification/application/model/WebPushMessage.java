package pl.karolbystrek.kairos.api.notification.application.model;

import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

public record WebPushMessage(
        @NonNull UUID eventId,
        @NonNull String endpoint,
        byte @NonNull [] p256dhKey,
        byte @NonNull [] authSecret,
        byte @NonNull [] payload,
        @NonNull Instant deadline
) {

    public WebPushMessage {
        p256dhKey = p256dhKey.clone();
        authSecret = authSecret.clone();
        payload = payload.clone();
    }

    @Override
    public byte[] p256dhKey() {
        return p256dhKey.clone();
    }

    @Override
    public byte[] authSecret() {
        return authSecret.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
