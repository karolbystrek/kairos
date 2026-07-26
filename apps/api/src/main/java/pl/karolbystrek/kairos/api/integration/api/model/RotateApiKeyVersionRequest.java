package pl.karolbystrek.kairos.api.integration.api.model;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RotateApiKeyVersionRequest(
        @NotNull(message = "API Key ID is required")
        UUID apiKeyId
) {
}
