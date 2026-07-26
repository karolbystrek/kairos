package pl.karolbystrek.kairos.api.integration.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateApiKeyRequest(
        @NotNull(message = "External Integration ID is required")
        UUID integrationId,

        @NotBlank(message = "Name is required")
        @CodePointLength(max = 64, message = "Name must contain at most 64 characters")
        @Pattern(
                regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$",
                message = "Name must be single-line text without control characters"
        )
        String name,

        @NotEmpty(message = "At least one API Key scope is required")
        Set<@NotBlank(message = "API Key scope is required") String> scopes,

        @NotEmpty(message = "At least one API Key location is required")
        Set<@NotNull(message = "API Key location is required") UUID> locationIds,

        Instant expiresAt
) {

    public CreateApiKeyRequest {
        name = name == null ? null : name.strip();
        scopes = scopes == null ? null : Set.copyOf(scopes);
        locationIds = locationIds == null ? null : Set.copyOf(locationIds);
    }
}
