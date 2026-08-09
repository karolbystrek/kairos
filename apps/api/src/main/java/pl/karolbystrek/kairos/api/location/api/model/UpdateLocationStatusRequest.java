package pl.karolbystrek.kairos.api.location.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

public record UpdateLocationStatusRequest(
    @NotNull(message = "Location status is required")
    LocationStatus status
) {
}
