package pl.karolbystrek.kairos.api.location.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(
    @NotBlank(message = "Location name is required")
    @Size(max = 120, message = "Location name must not exceed 120 characters")
    String name,
    @Size(max = 2048) String googleReviewUrl,
    @NotBlank(message = "Location time zone is required")
    @Size(max = 64, message = "Location time zone must not exceed 64 characters")
    String timeZone
) {
}
