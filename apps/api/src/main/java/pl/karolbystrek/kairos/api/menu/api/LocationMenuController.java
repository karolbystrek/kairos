package pl.karolbystrek.kairos.api.menu.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.menu.api.model.LocationMenuRequest;
import pl.karolbystrek.kairos.api.menu.api.model.LocationMenuResponse;
import pl.karolbystrek.kairos.api.menu.application.MenuService;

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
    LocationMenuResponse selectLocationMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID locationId,
        @RequestBody LocationMenuRequest request
    ) {
        return LocationMenuResponse.from(menuService.selectLocationMenu(principal, locationId, request.menuId()));
    }
}
