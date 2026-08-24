package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.SignedInRedemptionException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationPreview;
import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationView;
import pl.karolbystrek.kairos.api.account.application.model.CreatedAccountInvitation;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffLocation;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.StaffLocationDirectory;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitation;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationState;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationRevocationReason;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountInvitationRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountInvitationService {

    private final AccountInvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final StaffLocationDirectory locationDirectory;
    private final StaffAccessService staffAccessService;
    private final AccountCreationService accountCreationService;
    private final OneTimeBearerTokenService tokenService;
    private final Clock clock;

    @Transactional
    public CreatedAccountInvitation create(
        StaffPrincipal actor,
        java.util.UUID locationId,
        AssignmentRole role
    ) {
        var access = staffAccessService.resolveForUpdate(actor);
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
            toView(invitation, location, issuer.getUsername()),
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
                return toView(invitation, location, issuer.getUsername());
            })
            .toList();
    }

    @Transactional
    public void revoke(StaffPrincipal actor, java.util.UUID invitationId) {
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
    public void revokePendingByIssuer(java.util.UUID issuerAccountId) {
        revokePendingByIssuer(
            issuerAccountId,
            AccountInvitationRevocationReason.ISSUER_DISABLED
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokePendingByIssuer(
        java.util.UUID issuerAccountId,
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
        java.util.UUID locationId,
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
        var invitation = invitationRepository.findByTokenHash(tokenService.hash(presentedToken))
            .orElseThrow(AccountInvitationNotFoundException::new);
        var now = clock.instant();
        requireAvailable(invitation, now);
        requireIssuerAuthority(invitation);
        var location = requireLocation(invitation);
        requireEnabledLocation(location);
        return new AccountInvitationPreview(
            location.name(),
            invitation.getAssignmentRole(),
            invitation.getExpiresAt()
        );
    }

    @Transactional
    public StaffPrincipal redeem(
        PanelPrincipal signedInAccount,
        String presentedToken,
        String username,
        String email,
        String password
    ) {
        if (signedInAccount != null) {
            throw new SignedInRedemptionException();
        }

        var invitation = invitationRepository.findForUpdateByTokenHash(
                tokenService.hash(presentedToken)
            )
            .orElseThrow(AccountInvitationNotFoundException::new);
        var now = clock.instant();
        requireAvailable(invitation, now);
        requireIssuerAuthority(invitation);
        requireEnabledLocation(requireLocation(invitation));

        var account = accountCreationService.createMember(
            invitation.getTenantId(),
            username,
            email,
            password
        );
        var assignment = LocationAssignment.assign(
            account.getId(),
            invitation.getLocationId(),
            invitation.getTenantId(),
            invitation.getAssignmentRole(),
            now
        );
        assignmentRepository.saveAndFlush(assignment);
        invitation.redeem(account.getId(), now);
        invitationRepository.flush();
        log.info(
            "Account invitation {} redeemed into account {}",
            invitation.getId(),
            account.getId()
        );
        return new StaffPrincipal(account.getId(), account.getTenantId(), account.getTenantRole());
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

    private void requireIssuerAuthority(AccountInvitation invitation) {
        var issuer = accountRepository.findById(invitation.getIssuedByAccountId())
            .orElseThrow(() -> unavailable(AccountInvitationUnavailableException.Reason.REVOKED));
        try {
            var access = staffAccessService.resolve(new StaffPrincipal(
                issuer.getId(),
                issuer.getTenantId(),
                issuer.getTenantRole()
            ));
            access.requireLocationAccess(invitation.getTenantId(), invitation.getLocationId());
            requireManagementPermission(access, invitation.getAssignmentRole());
        }
        catch (StaffAccessDeniedException exception) {
            throw unavailable(AccountInvitationUnavailableException.Reason.REVOKED);
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
        String issuedByUsername
    ) {
        return new AccountInvitationView(
            invitation.getId(),
            invitation.getLocationId(),
            location.name(),
            invitation.getAssignmentRole(),
            issuedByUsername,
            invitation.getCreatedAt(),
            invitation.getExpiresAt()
        );
    }
}
