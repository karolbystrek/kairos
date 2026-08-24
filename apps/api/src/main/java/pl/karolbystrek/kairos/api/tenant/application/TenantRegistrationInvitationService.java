package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.application.port.TenantRegistrationInvitationRevoker;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;
import pl.karolbystrek.kairos.api.authentication.application.PanelAccessService;
import pl.karolbystrek.kairos.api.tenant.application.exception.InvalidTenantRegistrationInvitationRequestException;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationNotFoundException;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationUnavailableException;
import pl.karolbystrek.kairos.api.tenant.application.model.CreatedTenantRegistrationInvitation;
import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationInvitationPreview;
import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationInvitationView;
import pl.karolbystrek.kairos.api.tenant.domain.invitation.TenantRegistrationInvitation;
import pl.karolbystrek.kairos.api.tenant.domain.invitation.TenantRegistrationInvitationState;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRegistrationInvitationRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationInvitationService implements TenantRegistrationInvitationRevoker {

    private final TenantRegistrationInvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final PanelAccessService panelAccessService;
    private final OneTimeBearerTokenService tokenService;
    private final Clock clock;

    @Transactional
    public CreatedTenantRegistrationInvitation create(
        PlatformOperatorPrincipal actor,
        String candidateLabel
    ) {
        panelAccessService.requireEligibleForUpdate(actor);
        var label = normalizeLabel(candidateLabel);
        var issuer = accountRepository.findById(actor.accountId())
            .orElseThrow(() -> new StaffAccessDeniedException("The issuing account is not available"));
        var token = tokenService.generate();
        var invitation = TenantRegistrationInvitation.issue(
            label,
            actor.accountId(),
            token.hash(),
            clock.instant()
        );
        invitationRepository.saveAndFlush(invitation);
        log.info("Platform Operator {} issued tenant registration invitation {}", actor.accountId(), invitation.getId());
        return new CreatedTenantRegistrationInvitation(
            toView(invitation, issuer.getUsername()),
            token.value()
        );
    }

    @Transactional(readOnly = true)
    public List<TenantRegistrationInvitationView> listPending(PlatformOperatorPrincipal actor) {
        panelAccessService.requireEligible(actor);
        return invitationRepository.findAllByStateAndExpiresAtAfterOrderByCreatedAtDesc(
                TenantRegistrationInvitationState.PENDING,
                clock.instant()
            ).stream()
            .map(invitation -> {
                var issuer = accountRepository.findById(invitation.getIssuedByAccountId())
                    .orElseThrow(() -> new StaffAccessDeniedException("The invitation issuer is not available"));
                return toView(invitation, issuer.getUsername());
            })
            .toList();
    }

    @Transactional
    public void revoke(PlatformOperatorPrincipal actor, UUID invitationId) {
        panelAccessService.requireEligibleForUpdate(actor);
        var invitation = invitationRepository.findForUpdateById(invitationId)
            .orElseThrow(TenantRegistrationInvitationNotFoundException::new);
        if (!invitation.isPendingAt(clock.instant())) {
            throw new TenantRegistrationInvitationNotFoundException();
        }
        invitation.revokeByOperator(actor.accountId(), clock.instant());
        invitationRepository.flush();
        log.info("Platform Operator {} revoked tenant registration invitation {}", actor.accountId(), invitationId);
    }

    @Transactional(readOnly = true)
    public TenantRegistrationInvitationPreview preview(String presentedToken) {
        var tokenHash = tokenService.hashPresented(presentedToken)
            .orElseThrow(TenantRegistrationInvitationNotFoundException::new);
        var invitation = invitationRepository.findByTokenHash(tokenHash)
            .orElseThrow(TenantRegistrationInvitationNotFoundException::new);
        requireAvailable(invitation);
        return new TenantRegistrationInvitationPreview(invitation.getExpiresAt());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokePendingByIssuer(UUID issuerAccountId) {
        var now = clock.instant();
        invitationRepository.findAllForUpdateByIssuedByAccountIdAndState(
                issuerAccountId,
                TenantRegistrationInvitationState.PENDING
            ).stream()
            .filter(invitation -> invitation.isPendingAt(now))
            .forEach(invitation -> invitation.revokeForDisabledIssuer(now));
        invitationRepository.flush();
    }

    public void requireAvailable(TenantRegistrationInvitation invitation) {
        if (invitation.getState() == TenantRegistrationInvitationState.REDEEMED) {
            throw unavailable(TenantRegistrationInvitationUnavailableException.Reason.REDEEMED);
        }
        if (invitation.getState() == TenantRegistrationInvitationState.REVOKED) {
            throw unavailable(TenantRegistrationInvitationUnavailableException.Reason.REVOKED);
        }
        if (invitation.isExpiredAt(clock.instant())) {
            throw unavailable(TenantRegistrationInvitationUnavailableException.Reason.EXPIRED);
        }
    }

    private static String normalizeLabel(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new InvalidTenantRegistrationInvitationRequestException("Invitation label is required");
        }
        var label = candidate.strip();
        if (label.length() > 120) {
            throw new InvalidTenantRegistrationInvitationRequestException(
                "Invitation label must not exceed 120 characters"
            );
        }
        return label;
    }

    private static TenantRegistrationInvitationUnavailableException unavailable(
        TenantRegistrationInvitationUnavailableException.Reason reason
    ) {
        return new TenantRegistrationInvitationUnavailableException(reason);
    }

    private static TenantRegistrationInvitationView toView(
        TenantRegistrationInvitation invitation,
        String issuedByUsername
    ) {
        return new TenantRegistrationInvitationView(
            invitation.getId(),
            invitation.getLabel(),
            issuedByUsername,
            invitation.getCreatedAt(),
            invitation.getExpiresAt()
        );
    }
}
