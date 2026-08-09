package pl.karolbystrek.kairos.api.location.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findAllByTenantIdAndStatusNot(UUID tenantId, LocationStatus status);

    List<Location> findAllByIdInAndStatus(Collection<UUID> ids, LocationStatus status);

    boolean existsByTenantIdAndNormalizedNameAndStatusNot(
        UUID tenantId,
        String normalizedName,
        LocationStatus status
    );

    boolean existsByTenantIdAndNormalizedNameAndStatusNotAndIdNot(
        UUID tenantId,
        String normalizedName,
        LocationStatus status,
        UUID excludedId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Location> findForUpdateById(UUID locationId);
}
