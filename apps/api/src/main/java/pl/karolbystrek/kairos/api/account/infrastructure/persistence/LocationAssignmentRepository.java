package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignmentId;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationAssignmentRepository extends JpaRepository<LocationAssignment, LocationAssignmentId> {

    List<LocationAssignment> findAllByTenantId(UUID tenantId);

    List<LocationAssignment> findAllByTenantIdAndIdLocationIdAndRole(
        UUID tenantId,
        UUID locationId,
        AssignmentRole role
    );

    Optional<LocationAssignment> findByIdAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LocationAssignment> findForUpdateByIdAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<LocationAssignment> findAllForUpdateByTenantIdAndIdLocationId(
        UUID tenantId,
        UUID locationId
    );
}
