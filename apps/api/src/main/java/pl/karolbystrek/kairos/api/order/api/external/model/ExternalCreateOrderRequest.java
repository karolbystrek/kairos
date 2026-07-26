package pl.karolbystrek.kairos.api.order.api.external.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.UUID;

public record ExternalCreateOrderRequest(
        @NotNull(message = "Location is required")
        UUID locationId,

        @Size(min = 1, message = "Custom label must not be blank")
        @CodePointLength(max = 32, message = "Custom label must contain at most 32 characters")
        @Pattern(
                regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$",
                message = "Custom label must be single-line text without control characters"
        )
        String label
) {

    public ExternalCreateOrderRequest {
        label = label == null ? null : label.strip();
    }
}
