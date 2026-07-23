package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignmentId;

import java.util.Optional;
import java.util.UUID;

public interface LocationAssignmentRepository extends JpaRepository<LocationAssignment, LocationAssignmentId> {

    Optional<LocationAssignment> findByIdAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LocationAssignment> findForUpdateByIdAccountId(UUID accountId);
}
