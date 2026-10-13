package pl.karolbystrek.kairos.api.menu.api.external;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.menu.api.model.LocationMenuResponse;
import pl.karolbystrek.kairos.api.menu.application.MenuService;

import java.util.UUID;

@RestController
@RequestMapping("/external/locations/v1/{locationId}/menu")
@RequiredArgsConstructor
class ExternalLocationMenuController {

    private final MenuService menuService;

    @GetMapping
    LocationMenuResponse findLocationMenu(
        @AuthenticationPrincipal ApiKeyPrincipal principal,
        @PathVariable UUID locationId
    ) {
        return LocationMenuResponse.from(menuService.findLocationMenu(principal, locationId));
    }
}
