package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;

import java.time.Instant;
import java.util.UUID;

public record ExternalIntegrationView(
        @NonNull UUID id,
        @NonNull UUID tenantId,
        @NonNull String name,
        @NonNull ExternalIntegrationStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static ExternalIntegrationView from(ExternalIntegration integration) {
        return new ExternalIntegrationView(
                integration.getId(),
                integration.getTenantId(),
                integration.getName(),
                integration.getStatus(),
                integration.getCreatedAt(),
                integration.getUpdatedAt()
        );
    }
}
