package pl.karolbystrek.kairos.api.location.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.location.domain.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findAllByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Location> findForUpdateById(UUID locationId);
}
