package pl.karolbystrek.kairos.api.location.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.account.application.model.StaffLocation;
import pl.karolbystrek.kairos.api.account.application.port.StaffLocationDirectory;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class JpaStaffLocationDirectory implements StaffLocationDirectory {

    private final LocationRepository locationRepository;

    @Override
    public Optional<StaffLocation> findById(UUID locationId) {
        return locationRepository.findById(locationId)
            .map(location -> new StaffLocation(location.getId(), location.getTenantId(), location.getName()));
    }
}
