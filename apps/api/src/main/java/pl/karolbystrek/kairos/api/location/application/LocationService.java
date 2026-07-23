package pl.karolbystrek.kairos.api.location.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.location.application.model.LocationView;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final StaffAccessService staffAccessService;

    @Transactional(readOnly = true)
    public List<LocationView> listAccessible(StaffPrincipal principal) {
        var access = staffAccessService.resolve(principal);
        var locations = access.isTenantAdmin()
                ? locationRepository.findAllByTenantIdOrderByNameAsc(access.tenantId())
                : List.of(requireAssignedLocation(access));

        return locations.stream()
                .map(LocationView::from)
                .toList();
    }

    private Location requireAssignedLocation(StaffAccessContext access) {
        var location = locationRepository.findById(access.locationId())
                .orElseThrow(() -> new StaffAccessDeniedException("The assigned location is not available"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        return location;
    }
}
