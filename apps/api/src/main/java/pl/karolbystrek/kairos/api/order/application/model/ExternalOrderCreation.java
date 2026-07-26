package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;

import java.util.UUID;

public record ExternalOrderCreation(
        @NonNull UUID integrationId,
        @NonNull String idempotencyKey,
        @NonNull String requestFingerprint
) {
}
