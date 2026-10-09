package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationPreview;
import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationView;
import pl.karolbystrek.kairos.api.account.application.model.CreatedAccountInvitation;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffLocation;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.StaffLocationDirectory;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitation;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationRevocationReason;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationState;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountInvitationRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountInvitationService {

    private final JdbcTemplate database;
    private final AccountInvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final StaffLocationDirectory locationDirectory;
    private final StaffAccessService staffAccessService;
    private final OneTimeBearerTokenService tokenService;
    private final Clock clock;

    @Transactional
    public CreatedAccountInvitation create(
        StaffPrincipal actor,
        UUID locationId,
        AssignmentRole role
    ) {
        var access = staffAccessService.resolveForUpdate(actor);
        if (!access.isTenantAdmin()) access.requireLocationAccess(access.tenantId(), locationId);
        var location = locationDirectory.findById(locationId)
            .orElseThrow(() -> new AccountNotFoundException("Location was not found"));
        access.requireLocationAccess(location.tenantId(), location.id());
        requireEnabledLocation(location);
        requireManagementPermission(access, role);

        var issuer = accountRepository.findById(access.accountId())
            .orElseThrow(() -> new StaffAccessDeniedException("The issuing account is not available"));
        var token = tokenService.generate();
        var invitation = AccountInvitation.issue(
            access.tenantId(),
            location.id(),
            access.accountId(),
            role,
            token.hash(),
            clock.instant()
        );
        invitationRepository.saveAndFlush(invitation);
        log.info(
            "Account {} issued {} invitation {} for location {}",
            access.accountId(),
            role,
            invitation.getId(),
            location.id()
        );
        return new CreatedAccountInvitation(
            toView(invitation, location, issuer.getEmail()),
            token.value()
        );
    }

    @Transactional(readOnly = true)
    public List<AccountInvitationView> listPending(StaffPrincipal actor) {
        var access = staffAccessService.resolve(actor);
        requireInvitationCapability(access);
        var now = clock.instant();
        var invitations = access.isTenantAdmin()
            ? invitationRepository.findAllByTenantIdAndStateAndExpiresAtAfterOrderByCreatedAtDesc(
                access.tenantId(),
                AccountInvitationState.PENDING,
                now
            )
            : invitationRepository
                .findAllByTenantIdAndLocationIdAndAssignmentRoleAndStateAndExpiresAtAfterOrderByCreatedAtDesc(
                    access.tenantId(),
                    access.locationId(),
                    AssignmentRole.OPERATOR,
                    AccountInvitationState.PENDING,
                    now
                );
        return invitations.stream()
            .map(invitation -> {
                var location = requireLocation(invitation);
                var issuer = accountRepository.findById(invitation.getIssuedByAccountId())
                    .orElseThrow(() -> new StaffAccessDeniedException(
                        "The invitation issuer is not available"
                    ));
                return toView(invitation, location, issuer.getEmail());
            })
            .toList();
    }

    @Transactional
    public void revoke(StaffPrincipal actor, UUID invitationId) {
        var access = staffAccessService.resolveForUpdate(actor);
        requireInvitationCapability(access);
        var invitation = invitationRepository.findForUpdateById(invitationId)
            .orElseThrow(AccountInvitationNotFoundException::new);
        var now = clock.instant();
        requireAvailable(invitation, now);
        var location = requireLocation(invitation);
        access.requireLocationAccess(invitation.getTenantId(), location.id());
        requireManagementPermission(access, invitation.getAssignmentRole());
        invitation.revoke(AccountInvitationRevocationReason.STAFF_REVOKED, now);
        invitationRepository.flush();
        log.info("Account {} revoked invitation {}", access.accountId(), invitation.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokePendingByIssuer(UUID issuerAccountId) {
        revokePendingByIssuer(
            issuerAccountId,
            AccountInvitationRevocationReason.ISSUER_DISABLED
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokePendingByIssuer(
        UUID issuerAccountId,
        AccountInvitationRevocationReason reason
    ) {
        var now = clock.instant();
        invitationRepository.findAllForUpdateByIssuedByAccountIdAndState(
                issuerAccountId,
                AccountInvitationState.PENDING
            ).stream()
            .filter(invitation -> invitation.isPendingAt(now))
            .forEach(invitation -> invitation.revoke(reason, now));
        invitationRepository.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokePendingByLocation(
        UUID locationId,
        AccountInvitationRevocationReason reason
    ) {
        var now = clock.instant();
        invitationRepository.findAllForUpdateByLocationIdAndState(
                locationId,
                AccountInvitationState.PENDING
            ).stream()
            .filter(invitation -> invitation.isPendingAt(now))
            .forEach(invitation -> invitation.revoke(reason, now));
        invitationRepository.flush();
    }

    @Transactional(readOnly = true)
    public AccountInvitationPreview preview(String presentedToken) {
        var previews = database.query("SELECT * FROM public.invitation_preview(?, ?)", (row, index) -> {
            var state = AccountInvitationState.valueOf(row.getString("state"));
            if (state == AccountInvitationState.REDEEMED) throw unavailable(AccountInvitationUnavailableException.Reason.REDEEMED);
            if (state == AccountInvitationState.REVOKED) throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
            var expiresAt = row.getTimestamp("expires_at").toInstant();
            if (!clock.instant().isBefore(expiresAt)) throw unavailable(AccountInvitationUnavailableException.Reason.EXPIRED);
            if (!row.getBoolean("issuer_eligible") || !row.getBoolean("location_enabled")) {
                throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
            }
            return new AccountInvitationPreview(row.getString("location_name"),
                AssignmentRole.valueOf(row.getString("assignment_role")), expiresAt);
        }, tokenService.hash(presentedToken), Timestamp.from(clock.instant()));
        if (previews.isEmpty()) throw new AccountInvitationNotFoundException();
        return previews.getFirst();
    }

    @Transactional
    public StaffPrincipal redeem(String tokenHash, String email, String subject) {
        try {
            return database.queryForObject("SELECT * FROM public.redeem_invitation(?, ?, ?, ?)",
                (row, index) -> new StaffPrincipal(row.getObject("account_id", UUID.class),
                    row.getObject("tenant_id", UUID.class),
                    TenantRole.valueOf(row.getString("tenant_role"))),
                tokenHash, email, subject, Timestamp.from(clock.instant()));
        } catch (DataAccessException exception) {
            throw AccountCreationService.bootstrapFailure(exception);
        }
    }

    private static void requireManagementPermission(
        StaffAccessContext access,
        AssignmentRole targetRole
    ) {
        if (targetRole == null) {
            throw new InvalidAccountRequestException("Assignment role is required");
        }
        if (access.isTenantAdmin()) {
            return;
        }
        if (access.assignmentRole() != AssignmentRole.MANAGER
            || targetRole != AssignmentRole.OPERATOR) {
            throw new StaffAccessDeniedException("The account cannot invite this role");
        }
    }

    private static void requireInvitationCapability(StaffAccessContext access) {
        if (!access.isTenantAdmin() && access.assignmentRole() != AssignmentRole.MANAGER) {
            throw new StaffAccessDeniedException("The account cannot manage invitations");
        }
    }

    private StaffLocation requireLocation(AccountInvitation invitation) {
        var location = locationDirectory.findById(invitation.getLocationId())
            .orElseThrow(() -> unavailable(AccountInvitationUnavailableException.Reason.REVOKED));
        if (!location.tenantId().equals(invitation.getTenantId())) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
        }
        return location;
    }

    private static void requireEnabledLocation(StaffLocation location) {
        if (!location.isEnabled()) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
        }
    }

    private static void requireAvailable(AccountInvitation invitation, java.time.Instant now) {
        if (invitation.getState() == AccountInvitationState.REDEEMED) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REDEEMED);
        }
        if (invitation.getState() == AccountInvitationState.REVOKED) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
        }
        if (invitation.isExpiredAt(now)) {
            throw unavailable(AccountInvitationUnavailableException.Reason.EXPIRED);
        }
    }

    private static AccountInvitationUnavailableException unavailable(
        AccountInvitationUnavailableException.Reason reason
    ) {
        return new AccountInvitationUnavailableException(reason);
    }

    private static AccountInvitationView toView(
        AccountInvitation invitation,
        StaffLocation location,
        String issuedByEmail
    ) {
        return new AccountInvitationView(
            invitation.getId(),
            invitation.getLocationId(),
            location.name(),
            invitation.getAssignmentRole(),
            issuedByEmail,
            invitation.getCreatedAt(),
            invitation.getExpiresAt()
        );
    }
}
