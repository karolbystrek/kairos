package pl.karolbystrek.kairos.api.location.application.model;

import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

import java.time.Instant;
import java.util.UUID;

public record LocationView(
        UUID id,
        String name,
        String timeZone,
        LocationStatus status,
        Instant createdAt,
        Instant updatedAt,
        String googleReviewUrl
) {
    public static LocationView from(Location location) {
        return new LocationView(
            location.getId(),
            location.getName(),
            location.getTimeZone(),
            location.getStatus(),
            location.getCreatedAt(),
            location.getUpdatedAt(),
            location.getGoogleReviewUrl()
        );
    }
}
