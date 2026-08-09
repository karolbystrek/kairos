package pl.karolbystrek.kairos.api.location.application.model;

import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

import java.time.Instant;
import java.util.UUID;

public record LocationView(
        UUID id,
        String name,
        LocationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static LocationView from(Location location) {
        return new LocationView(
            location.getId(),
            location.getName(),
            location.getStatus(),
            location.getCreatedAt(),
            location.getUpdatedAt()
        );
    }
}
