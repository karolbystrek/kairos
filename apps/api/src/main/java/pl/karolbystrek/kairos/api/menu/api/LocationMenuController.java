package pl.karolbystrek.kairos.api.menu.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.menu.api.model.LocationMenuResponse;
import pl.karolbystrek.kairos.api.menu.api.model.MenuRequest;
import pl.karolbystrek.kairos.api.menu.application.MenuService;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/locations/v1/{locationId}/menu")
@RequiredArgsConstructor
class LocationMenuController {

    private final MenuService menuService;

    @GetMapping
    LocationMenuResponse findLocationMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId
    ) {
        return LocationMenuResponse.from(menuService.findLocationMenu(principal, locationId));
    }

    @PutMapping
    LocationMenuResponse saveLocationMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId,
        @Valid @RequestBody MenuRequest request
    ) {
        return LocationMenuResponse.from(Optional.of(menuService.saveLocationMenu(principal, locationId, request.items())));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLocationMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId
    ) {
        menuService.deleteLocationMenu(principal, locationId);
    }
}
