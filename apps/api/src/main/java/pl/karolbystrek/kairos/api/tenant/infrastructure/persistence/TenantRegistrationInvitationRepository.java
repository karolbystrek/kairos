package pl.karolbystrek.kairos.api.tenant.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.tenant.domain.invitation.TenantRegistrationInvitation;
import pl.karolbystrek.kairos.api.tenant.domain.invitation.TenantRegistrationInvitationState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRegistrationInvitationRepository
    extends JpaRepository<TenantRegistrationInvitation, UUID> {

    Optional<TenantRegistrationInvitation> findByTokenHash(String tokenHash);

    List<TenantRegistrationInvitation> findAllByStateAndExpiresAtAfterOrderByCreatedAtDesc(
        TenantRegistrationInvitationState state,
        Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantRegistrationInvitation> findForUpdateById(UUID invitationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantRegistrationInvitation> findForUpdateByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TenantRegistrationInvitation> findAllForUpdateByIssuedByAccountIdAndState(
        UUID accountId,
        TenantRegistrationInvitationState state
    );
}
