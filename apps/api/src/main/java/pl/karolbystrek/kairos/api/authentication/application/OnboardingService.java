package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {
    public record Registration(StaffPrincipal principal, ZitadelClient.ProviderSession session) {}
    private final AccountCreationService accounts;
    private final AccountInvitationService invitations;
    private final OneTimeBearerTokenService tokens;
    private final TenantRegistrationService tenants;
    private final ZitadelClient provider;

    public Registration register(String email, String password, String invitation) {
        if (accounts.identityConflict(email, null))
            throw new RegistrationValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists. Sign in instead.");
        if (invitation != null) invitations.preview(invitation);
        String subject;
        try {
            subject = provider.createUser(email, password);
        }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new RegistrationValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists. Sign in instead.");
            }
            throw exception;
        }
        ZitadelClient.ProviderSession session = null;
        try {
            session = provider.signIn(email, password);
            if (!subject.equals(session.userId())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            var principal = invitation == null ? tenants.register(email, subject)
                : invitations.redeem(tokens.hash(invitation), email, subject);
            return new Registration(principal, session);
        } catch (RuntimeException exception) {
            if (session != null) provider.terminate(session);
            try { provider.deleteUser(subject); }
            catch (RuntimeException cleanup) { log.error("ZITADEL registration cleanup failed for user {}", subject); }
            throw exception;
        }
    }
}
