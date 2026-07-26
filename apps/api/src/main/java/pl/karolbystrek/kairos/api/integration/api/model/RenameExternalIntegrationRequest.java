package pl.karolbystrek.kairos.api.integration.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

public record RenameExternalIntegrationRequest(
        @NotBlank(message = "Name is required")
        @CodePointLength(max = 64, message = "Name must contain at most 64 characters")
        @Pattern(
                regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$",
                message = "Name must be single-line text without control characters"
        )
        String name
) {

    public RenameExternalIntegrationRequest {
        name = name == null ? null : name.strip();
    }
}
