package pl.karolbystrek.kairos.api.location.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.location.domain.Location;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findAllByTenantIdOrderByNameAsc(UUID tenantId);
}
