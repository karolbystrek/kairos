package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {
    public record Registration(StaffPrincipal principal, ZitadelClient.ProviderSession session) {}
    private final AccountInvitationService invitations;
    private final OneTimeBearerTokenService tokens;
    private final ZitadelClient provider;
    private final RegistrationVerificationService verification;
    private final PlatformTransactionManager transactions;

    public Registration register(String email, String password, String invitation) {
        if (invitation == null || invitation.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is required");
        return verification.nested(() -> new TransactionTemplate(transactions).execute(status -> provision(email, password, invitation)));
    }

    private Registration provision(String email, String password, String invitation) {
        var preview = invitations.preview(invitation);
        AccountInvitationService.requireRecipientEmail(preview.email(), email);
        verification.prepareIdentity(email);
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
            provider.markEmailVerified(subject, email);
            var principal = invitations.redeem(tokens.hash(invitation), email, subject);
            return new Registration(principal, session);
        } catch (RuntimeException exception) {
            if (session != null) provider.terminate(session);
            try { provider.deleteUser(subject); }
            catch (RuntimeException cleanup) { log.error("ZITADEL registration cleanup failed for user {}", subject); }
            throw exception;
        }
    }
}
