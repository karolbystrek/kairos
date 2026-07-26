package pl.karolbystrek.kairos.api.integration.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;

public record UpdateExternalIntegrationStatusRequest(
        @NotNull(message = "External Integration status is required")
        ExternalIntegrationStatus status
) {
}
