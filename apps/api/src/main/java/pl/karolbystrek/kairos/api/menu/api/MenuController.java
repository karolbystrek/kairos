package pl.karolbystrek.kairos.api.menu.api;

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
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.menu.api.model.MenuRequest;
import pl.karolbystrek.kairos.api.menu.api.model.MenuResponse;
import pl.karolbystrek.kairos.api.menu.application.MenuService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/menus/v1")
@RequiredArgsConstructor
class MenuController {

    private final MenuService menuService;

    @GetMapping
    List<MenuResponse> listMenus(@AuthenticationPrincipal StaffPrincipal principal) {
        return menuService.list(principal).stream()
            .map(MenuResponse::from)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MenuResponse createMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @Valid @RequestBody MenuRequest request
    ) {
        return MenuResponse.from(menuService.create(principal, request.name(), request.items()));
    }

    @PutMapping("/{menuId}")
    MenuResponse updateMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID menuId,
        @Valid @RequestBody MenuRequest request
    ) {
        return MenuResponse.from(menuService.update(principal, menuId, request.name(), request.items()));
    }

    @DeleteMapping("/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMenu(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID menuId
    ) {
        menuService.delete(principal, menuId);
    }
}
