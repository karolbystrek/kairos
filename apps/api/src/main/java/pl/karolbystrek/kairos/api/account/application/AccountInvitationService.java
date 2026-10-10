package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitation;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationRevocationReason;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationState;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountInvitationRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountInvitationService {

    private final DatabaseAccessContext databaseAccess;
    private final AccountCreationService accountCreationService;
    private final LocationAssignmentRepository assignmentRepository;
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
        AssignmentRole role,
        String email
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
            email.strip().toLowerCase(Locale.ROOT),
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
        var hash = tokenService.hash(presentedToken);
        databaseAccess.invitation(hash, null);
        var invitation = invitationRepository.findByTokenHash(hash)
            .orElseThrow(AccountInvitationNotFoundException::new);
        requireAvailable(invitation, clock.instant());
        requireIssuerAuthority(invitation);
        var location = requireLocation(invitation);
        requireEnabledLocation(location);
        return new AccountInvitationPreview(invitation.getEmail(), location.name(), invitation.getAssignmentRole(), invitation.getExpiresAt());
    }

    @Transactional
    public StaffPrincipal redeem(String tokenHash, String email, String subject) {
        var accountId = UUID.randomUUID();
        databaseAccess.invitation(tokenHash, accountId);
        var locationId = invitationRepository.findLocationIdByTokenHash(tokenHash)
            .orElseThrow(AccountInvitationNotFoundException::new);
        // Location-before-invitation keeps redemption in the same lock order as lifecycle cascades.
        var location = locationDirectory.findForShareById(locationId)
            .orElseThrow(() -> unavailable(AccountInvitationUnavailableException.Reason.REVOKED));
        var invitation = invitationRepository.findForUpdateByTokenHash(tokenHash)
            .orElseThrow(AccountInvitationNotFoundException::new);
        var now = clock.instant();
        requireAvailable(invitation, now);
        requireRecipientEmail(invitation.getEmail(), email);
        requireIssuerAuthority(invitation);
        requireEnabledLocation(location);
        var account = accountCreationService.create(Account.provisionMember(
            accountId, invitation.getTenantId(), email, subject, now));
        assignmentRepository.saveAndFlush(LocationAssignment.assign(account.getId(), invitation.getLocationId(),
            invitation.getTenantId(), invitation.getAssignmentRole(), now));
        invitation.redeem(account.getId(), now);
        invitationRepository.flush();
        log.info("Account invitation {} redeemed into account {}", invitation.getId(), account.getId());
        return new StaffPrincipal(account.getId(), account.getTenantId(), account.getTenantRole());
    }

    public static void requireRecipientEmail(String invitedEmail, String email) {
        if (!invitedEmail.equals(email.strip().toLowerCase(Locale.ROOT))) {
            throw new RegistrationValidationException(HttpStatus.BAD_REQUEST, "email",
                "Email must match the invitation.");
        }
    }

    private void requireIssuerAuthority(AccountInvitation invitation) {
        var issuer = accountRepository.findById(invitation.getIssuedByAccountId())
            .orElseThrow(() -> unavailable(AccountInvitationUnavailableException.Reason.REVOKED));
        if (!issuer.isEnabled() || !issuer.getTenantId().equals(invitation.getTenantId())) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
        }
        if (issuer.getTenantRole() == TenantRole.ADMIN) return;
        var assignment = assignmentRepository.findByIdAccountId(issuer.getId())
            .orElseThrow(() -> unavailable(AccountInvitationUnavailableException.Reason.REVOKED));
        if (!assignment.getLocationId().equals(invitation.getLocationId())
            || assignment.getRole() != AssignmentRole.MANAGER || invitation.getAssignmentRole() != AssignmentRole.OPERATOR) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
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

    private static void requireAvailable(AccountInvitation invitation, Instant now) {
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
            invitation.getEmail(),
            invitation.getLocationId(),
            location.name(),
            invitation.getAssignmentRole(),
            issuedByEmail,
            invitation.getCreatedAt(),
            invitation.getExpiresAt()
        );
    }
}
