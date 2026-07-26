package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ApiKeyView(
        @NonNull UUID id,
        @NonNull UUID integrationId,
        @NonNull UUID tenantId,
        @NonNull String name,
        @NonNull List<String> scopes,
        @NonNull List<UUID> locationIds,
        Instant expiresAt,
        Instant revokedAt,
        @NonNull Instant createdAt
) {

    public static ApiKeyView from(ApiKey apiKey) {
        var scopes = apiKey.getScopes().stream()
                .map(ApiKeyScope::externalValue)
                .sorted()
                .toList();
        var locationIds = apiKey.getLocationIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        return new ApiKeyView(
                apiKey.getId(),
                apiKey.getIntegrationId(),
                apiKey.getTenantId(),
                apiKey.getName(),
                scopes,
                locationIds,
                apiKey.getExpiresAt(),
                apiKey.getRevokedAt(),
                apiKey.getCreatedAt()
        );
    }
}
