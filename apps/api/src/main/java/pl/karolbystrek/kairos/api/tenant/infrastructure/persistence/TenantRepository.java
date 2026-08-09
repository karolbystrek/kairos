package pl.karolbystrek.kairos.api.tenant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;

import java.util.UUID;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tenant> findForUpdateById(UUID tenantId);
}
