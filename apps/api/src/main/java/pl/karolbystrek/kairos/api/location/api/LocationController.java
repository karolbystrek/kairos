package pl.karolbystrek.kairos.api.location.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.location.api.model.LocationResponse;
import pl.karolbystrek.kairos.api.location.application.LocationService;

import java.util.List;

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
}
