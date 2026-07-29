package pl.karolbystrek.kairos.api.notification.application.model;

import lombok.NonNull;

import java.time.Instant;

public record CustomerPushSubscriptionInput(
        @NonNull String endpoint,
        @NonNull String p256dh,
        @NonNull String auth,
        Instant expirationTime
) {
}
