package pl.karolbystrek.kairos.api.menu.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.menu.domain.Menu;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    List<Menu> findAllByTenantIdOrderByNormalizedNameAscIdAsc(UUID tenantId);

    boolean existsByTenantIdAndNormalizedName(UUID tenantId, String normalizedName);

    boolean existsByTenantIdAndNormalizedNameAndIdNot(UUID tenantId, String normalizedName, UUID excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Menu> findForUpdateById(UUID menuId);
}
