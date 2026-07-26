package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record ApiKeyPrincipal(
        @NonNull UUID tenantId,
        @NonNull UUID integrationId,
        @NonNull UUID apiKeyId,
        @NonNull UUID apiKeyVersionId,
        @NonNull Set<ApiKeyScope> scopes,
        @NonNull Set<UUID> locationIds
) implements Principal {

    public ApiKeyPrincipal {
        scopes = Set.copyOf(scopes);
        locationIds = Set.copyOf(locationIds);
    }

    @Override
    public String getName() {
        return apiKeyVersionId.toString();
    }

    public boolean grants(ApiKeyScope required) {
        return scopes.stream().anyMatch(scope -> scope.grants(required));
    }

    public void requireScope(@NonNull ApiKeyScope required) {
        if (!grants(required)) {
            throw new IntegrationAccessDeniedException("The API Key does not grant the required scope");
        }
    }

    public boolean canAccessLocation(@NonNull UUID locationId) {
        return locationIds.contains(locationId);
    }

    public void requireLocationAccess(@NonNull UUID locationId) {
        if (!canAccessLocation(locationId)) {
            throw new IntegrationAccessDeniedException("The API Key cannot access this location");
        }
    }
}
