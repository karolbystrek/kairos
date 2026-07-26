package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        UUID integrationId,
        UUID tenantId,
        String name,
        List<String> scopes,
        List<UUID> locationIds,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {

    public static ApiKeyResponse from(ApiKeyView apiKey) {
        return new ApiKeyResponse(
                apiKey.id(),
                apiKey.integrationId(),
                apiKey.tenantId(),
                apiKey.name(),
                apiKey.scopes(),
                apiKey.locationIds(),
                apiKey.expiresAt(),
                apiKey.revokedAt(),
                apiKey.createdAt()
        );
    }
}
