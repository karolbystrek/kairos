package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitation;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationState;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, UUID> {

    Optional<AccountInvitation> findByTokenHash(String tokenHash);

    @Query("SELECT i.locationId FROM AccountInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<UUID> findLocationIdByTokenHash(String tokenHash);

    List<AccountInvitation> findAllByTenantIdAndStateAndExpiresAtAfterOrderByCreatedAtDesc(
        UUID tenantId,
        AccountInvitationState state,
        Instant now
    );

    List<AccountInvitation> findAllByTenantIdAndLocationIdAndAssignmentRoleAndStateAndExpiresAtAfterOrderByCreatedAtDesc(
        UUID tenantId,
        UUID locationId,
        AssignmentRole assignmentRole,
        AccountInvitationState state,
        Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountInvitation> findForUpdateByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountInvitation> findForUpdateById(UUID invitationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountInvitation> findAllForUpdateByIssuedByAccountIdAndState(
        UUID accountId,
        AccountInvitationState state
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountInvitation> findAllForUpdateByLocationIdAndState(
        UUID locationId,
        AccountInvitationState state
    );
}
