package pl.karolbystrek.kairos.api.location.api.model;

import pl.karolbystrek.kairos.api.location.application.model.LocationView;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String name,
        LocationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static LocationResponse from(LocationView location) {
        return new LocationResponse(
            location.id(),
            location.name(),
            location.status(),
            location.createdAt(),
            location.updatedAt()
        );
    }
}
