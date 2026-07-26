package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.ExternalIntegrationView;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;

import java.time.Instant;
import java.util.UUID;

public record ExternalIntegrationResponse(
        UUID id,
        UUID tenantId,
        String name,
        ExternalIntegrationStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ExternalIntegrationResponse from(ExternalIntegrationView integration) {
        return new ExternalIntegrationResponse(
                integration.id(),
                integration.tenantId(),
                integration.name(),
                integration.status(),
                integration.createdAt(),
                integration.updatedAt()
        );
    }
}
