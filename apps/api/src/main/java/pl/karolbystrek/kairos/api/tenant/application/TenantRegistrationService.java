package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;
import pl.karolbystrek.kairos.api.authentication.application.model.IssuedSession;
import pl.karolbystrek.kairos.api.tenant.application.exception.SignedInTenantRegistrationException;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationNotFoundException;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRegistrationInvitationRepository;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final TenantRegistrationInvitationRepository invitationRepository;
    private final TenantRegistrationInvitationService invitationService;
    private final OneTimeBearerTokenService tokenService;
    private final AccountCreationService accountCreationService;
    private final AuthenticationSessionService sessionService;
    private final Clock clock;

    @Transactional
    public IssuedSession register(
        PanelPrincipal signedInAccount,
        String presentedToken,
        String administratorUsername,
        String administratorEmail,
        String administratorPassword
    ) {
        if (signedInAccount != null) {
            throw new SignedInTenantRegistrationException();
        }

        var tokenHash = tokenService.hashPresented(presentedToken)
            .orElseThrow(TenantRegistrationInvitationNotFoundException::new);
        var invitation = invitationRepository.findForUpdateByTokenHash(tokenHash)
            .orElseThrow(TenantRegistrationInvitationNotFoundException::new);
        invitationService.requireAvailable(invitation);

        var tenant = Tenant.create();
        tenantRepository.save(tenant);
        var administrator = accountCreationService.createAdministrator(
            tenant.getId(),
            administratorUsername,
            administratorEmail,
            administratorPassword
        );
        var principal = new StaffPrincipal(
            administrator.getId(),
            tenant.getId(),
            administrator.getTenantRole()
        );
        invitation.redeem(tenant.getId(), administrator.getId(), clock.instant());
        invitationRepository.flush();
        var session = sessionService.start(principal);

        log.info(
            "Redeemed tenant registration invitation {} into tenant {} and administrator account {}",
            invitation.getId(),
            tenant.getId(),
            administrator.getId()
        );
        return session;
    }
}
