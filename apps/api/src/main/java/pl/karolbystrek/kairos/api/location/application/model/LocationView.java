package pl.karolbystrek.kairos.api.location.application.model;

import pl.karolbystrek.kairos.api.location.domain.Location;

import java.util.UUID;

public record LocationView(
        UUID id
) {
    public static LocationView from(Location location) {
        return new LocationView(location.getId());
    }
}
