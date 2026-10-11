package pl.karolbystrek.kairos.api.menu.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.menu.application.exception.InvalidMenuRequestException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuConflictException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;
import pl.karolbystrek.kairos.api.menu.application.model.MenuView;
import pl.karolbystrek.kairos.api.menu.domain.LocationMenu;
import pl.karolbystrek.kairos.api.menu.domain.Menu;
import pl.karolbystrek.kairos.api.menu.domain.MenuDefinition;
import pl.karolbystrek.kairos.api.menu.infrastructure.persistence.LocationMenuRepository;
import pl.karolbystrek.kairos.api.menu.infrastructure.persistence.MenuRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final MenuRepository menuRepository;
    private final LocationMenuRepository locationMenuRepository;
    private final LocationRepository locationRepository;
    private final StaffAccessService staffAccessService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MenuView> list(StaffPrincipal principal) {
        var access = staffAccessService.resolve(principal);
        return menuRepository.findAllByTenantIdOrderByNormalizedNameAscIdAsc(access.tenantId()).stream()
            .map(MenuView::from)
            .toList();
    }

    @Transactional
    public MenuView create(StaffPrincipal principal, String name, List<String> items) {
        var access = requireAdministrator(principal);
        var definition = parseDefinition(name, items);
        if (menuRepository.existsByTenantIdAndNormalizedName(access.tenantId(), definition.normalizedName())) {
            throw nameConflict(null);
        }
        var menu = Menu.create(access.tenantId(), definition, clock.instant());
        try {
            menuRepository.saveAndFlush(menu);
        }
        catch (DataIntegrityViolationException exception) {
            throw nameConflict(exception);
        }
        log.info("Account {} created menu {}", access.accountId(), menu.getId());
        return MenuView.from(menu);
    }

    @Transactional
    public MenuView update(StaffPrincipal principal, UUID menuId, String name, List<String> items) {
        var access = requireAdministrator(principal);
        var menu = requireMenuForUpdate(access, menuId);
        var definition = parseDefinition(name, items);
        if (menuRepository.existsByTenantIdAndNormalizedNameAndIdNot(
            access.tenantId(),
            definition.normalizedName(),
            menu.getId()
        )) {
            throw nameConflict(null);
        }
        menu.update(definition, clock.instant());
        try {
            menuRepository.flush();
        }
        catch (DataIntegrityViolationException exception) {
            throw nameConflict(exception);
        }
        log.info("Account {} updated menu {}", access.accountId(), menu.getId());
        return MenuView.from(menu);
    }

    @Transactional
    public void delete(StaffPrincipal principal, UUID menuId) {
        var access = requireAdministrator(principal);
        var menu = requireMenuForUpdate(access, menuId);
        if (locationMenuRepository.existsByMenuId(menu.getId())) {
            throw inUse(null);
        }
        try {
            menuRepository.delete(menu);
            menuRepository.flush();
        }
        catch (DataIntegrityViolationException exception) {
            throw inUse(exception);
        }
        log.info("Account {} deleted menu {}", access.accountId(), menu.getId());
    }

    @Transactional(readOnly = true)
    public Optional<MenuView> findLocationMenu(StaffPrincipal principal, UUID locationId) {
        var access = staffAccessService.resolve(principal);
        requireLocation(access, locationId);
        return activeMenu(locationId).map(MenuView::from);
    }

    @Transactional
    public Optional<MenuView> selectLocationMenu(StaffPrincipal principal, UUID locationId, UUID menuId) {
        var access = staffAccessService.resolveForUpdate(principal);
        if (!access.isTenantAdmin() && access.assignmentRole() != AssignmentRole.MANAGER) {
            throw new StaffAccessDeniedException("Only an administrator or a location manager can choose a menu");
        }
        var location = locationRepository.findForUpdateById(locationId)
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(() -> new MenuNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        var current = locationMenuRepository.findById(locationId);
        if (menuId == null) {
            current.ifPresent(locationMenuRepository::delete);
            locationMenuRepository.flush();
            log.info("Account {} cleared the menu of location {}", access.accountId(), locationId);
            return Optional.empty();
        }
        var menu = menuRepository.findById(menuId)
            .filter(candidate -> candidate.getTenantId().equals(access.tenantId()))
            .orElseThrow(() -> new MenuNotFoundException("Menu was not found"));
        var now = clock.instant();
        current.ifPresentOrElse(
            locationMenu -> locationMenu.change(menu, now),
            () -> locationMenuRepository.save(LocationMenu.assign(locationId, menu, now))
        );
        try {
            locationMenuRepository.flush();
        }
        catch (DataIntegrityViolationException exception) {
            // A concurrent delete removed the menu after it was read; managers cannot lock menus under RLS.
            throw new MenuNotFoundException("Menu was not found");
        }
        log.info("Account {} set menu {} for location {}", access.accountId(), menu.getId(), locationId);
        return Optional.of(MenuView.from(menu));
    }

    // Callers authorize the location first; row policies still scope the read.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> activeMenuItems(UUID locationId) {
        return activeMenu(locationId).map(Menu::getItems).orElse(List.of());
    }

    private Optional<Menu> activeMenu(UUID locationId) {
        return locationMenuRepository.findById(locationId)
            .flatMap(locationMenu -> menuRepository.findById(locationMenu.getMenuId()));
    }

    private void requireLocation(StaffAccessContext access, UUID locationId) {
        var location = locationRepository.findById(locationId)
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(() -> new MenuNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
    }

    private StaffAccessContext requireAdministrator(StaffPrincipal principal) {
        var access = staffAccessService.resolveForUpdate(principal);
        if (!access.isTenantAdmin()) {
            throw new StaffAccessDeniedException("Only a tenant administrator can manage menus");
        }
        return access;
    }

    private Menu requireMenuForUpdate(StaffAccessContext access, UUID menuId) {
        return menuRepository.findForUpdateById(menuId)
            .filter(candidate -> candidate.getTenantId().equals(access.tenantId()))
            .orElseThrow(() -> new MenuNotFoundException("Menu was not found"));
    }

    private static MenuDefinition parseDefinition(String name, List<String> items) {
        try {
            return MenuDefinition.from(name, items);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidMenuRequestException(exception.getMessage(), exception);
        }
    }

    private static MenuConflictException nameConflict(Throwable cause) {
        return new MenuConflictException(
            MenuConflictException.Reason.NAME_CONFLICT,
            "A menu with that name already exists",
            cause
        );
    }

    private static MenuConflictException inUse(Throwable cause) {
        return new MenuConflictException(
            MenuConflictException.Reason.IN_USE,
            "Choose another menu for every location using this menu before deleting it",
            cause
        );
    }
}
