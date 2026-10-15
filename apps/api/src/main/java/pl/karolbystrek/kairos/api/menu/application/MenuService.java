package pl.karolbystrek.kairos.api.menu.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.menu.application.exception.InvalidMenuRequestException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;
import pl.karolbystrek.kairos.api.menu.application.model.MenuView;
import pl.karolbystrek.kairos.api.menu.domain.LocationMenu;
import pl.karolbystrek.kairos.api.menu.domain.MenuDefinition;
import pl.karolbystrek.kairos.api.menu.infrastructure.persistence.LocationMenuRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final LocationMenuRepository locationMenuRepository;
    private final LocationRepository locationRepository;
    private final StaffAccessService staffAccessService;
    private final Clock clock;
    private final DatabaseAccessContext databaseAccess;

    @Transactional(readOnly = true)
    public Optional<MenuView> findLocationMenu(StaffPrincipal principal, UUID locationId) {
        var access = staffAccessService.resolve(principal);
        var location = locationRepository.findById(locationId)
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(() -> new MenuNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        return locationMenuRepository.findById(locationId).map(MenuView::from);
    }

    @Transactional(readOnly = true)
    public Optional<MenuView> findLocationMenu(ApiKeyPrincipal principal, UUID locationId) {
        databaseAccess.integration(principal);
        principal.requireScope(ApiKeyScope.ORDERS_READ);
        principal.requireLocationAccess(locationId);
        locationRepository.findById(locationId)
            .filter(location -> location.getTenantId().equals(principal.tenantId()) && !location.isArchived())
            .orElseThrow(() -> new MenuNotFoundException("Location was not found"));
        return locationMenuRepository.findById(locationId).map(MenuView::from);
    }

    @Transactional
    public MenuView saveLocationMenu(StaffPrincipal principal, UUID locationId, List<String> items) {
        var access = staffAccessService.resolveForUpdate(principal);
        var location = requireManagedLocation(access, locationId);
        var definition = parseDefinition(items);
        var now = clock.instant();
        var menu = locationMenuRepository.findById(locationId)
            .map(current -> {
                current.update(definition, now);
                return current;
            })
            .orElseGet(() -> locationMenuRepository.save(
                LocationMenu.create(location.getId(), location.getTenantId(), definition, now)
            ));
        locationMenuRepository.flush();
        log.info("Account {} saved the menu of location {}", access.accountId(), locationId);
        return MenuView.from(menu);
    }

    @Transactional
    public void deleteLocationMenu(StaffPrincipal principal, UUID locationId) {
        var access = staffAccessService.resolveForUpdate(principal);
        requireManagedLocation(access, locationId);
        locationMenuRepository.findById(locationId).ifPresent(locationMenuRepository::delete);
        locationMenuRepository.flush();
        log.info("Account {} deleted the menu of location {}", access.accountId(), locationId);
    }

    // Callers authorize the location first; row policies still scope the read.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> menuItems(UUID locationId) {
        return locationMenuRepository.findById(locationId).map(LocationMenu::getItems).orElse(List.of());
    }

    // Locks the location so concurrent first saves of its menu serialize.
    private Location requireManagedLocation(StaffAccessContext access, UUID locationId) {
        access.requireAdminOrManager("Only an administrator or a location manager can change a menu");
        var location = locationRepository.findForUpdateById(locationId)
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(() -> new MenuNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        return location;
    }

    private static MenuDefinition parseDefinition(List<String> items) {
        try {
            return MenuDefinition.from(items);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidMenuRequestException(exception.getMessage(), exception);
        }
    }
}
