package pl.karolbystrek.kairos.api.location.api.model;

import pl.karolbystrek.kairos.api.location.application.model.LocationView;

import java.util.UUID;

public record LocationResponse(
        UUID id
) {
    public static LocationResponse from(LocationView location) {
        return new LocationResponse(location.id());
    }
}
