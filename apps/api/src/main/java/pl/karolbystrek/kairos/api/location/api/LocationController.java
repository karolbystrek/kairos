package pl.karolbystrek.kairos.api.location.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.location.api.model.LocationNameRequest;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.location.api.model.LocationResponse;
import pl.karolbystrek.kairos.api.location.api.model.UpdateLocationStatusRequest;
import pl.karolbystrek.kairos.api.location.application.LocationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/v1")
@RequiredArgsConstructor
class LocationController {

    private final LocationService locationService;

    @GetMapping
    List<LocationResponse> listLocations(@AuthenticationPrincipal StaffPrincipal principal) {
        var locations = locationService.listAccessible(principal);
        return locations.stream()
                .map(LocationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LocationResponse createLocation(
        @AuthenticationPrincipal StaffPrincipal principal,
        @Valid @RequestBody LocationNameRequest request
    ) {
        return LocationResponse.from(locationService.create(principal, request.name()));
    }

    @PutMapping("/{locationId}")
    LocationResponse renameLocation(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId,
        @Valid @RequestBody LocationNameRequest request
    ) {
        return LocationResponse.from(locationService.rename(principal, locationId, request.name()));
    }

    @PutMapping("/{locationId}/status")
    LocationResponse updateLocationStatus(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId,
        @Valid @RequestBody UpdateLocationStatusRequest request
    ) {
        return LocationResponse.from(
            locationService.updateStatus(principal, locationId, request.status())
        );
    }

    @DeleteMapping("/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLocation(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId
    ) {
        locationService.delete(principal, locationId);
    }
}
