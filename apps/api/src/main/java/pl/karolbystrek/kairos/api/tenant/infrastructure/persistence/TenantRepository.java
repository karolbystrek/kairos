package pl.karolbystrek.kairos.api.tenant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
