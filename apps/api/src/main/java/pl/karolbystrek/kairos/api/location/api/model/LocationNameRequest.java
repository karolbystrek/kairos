package pl.karolbystrek.kairos.api.location.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationNameRequest(
    @NotBlank(message = "Location name is required")
    @Size(max = 120, message = "Location name must not exceed 120 characters")
    String name
) {
}
