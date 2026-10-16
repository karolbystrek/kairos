package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    public record Registration(StaffPrincipal principal, String subject) {}
    private final AccountInvitationService invitations;
    private final OneTimeBearerTokenService tokens;
    private final StaffIdentityStore identities;
    private final RegistrationVerificationService verification;
    private final PlatformTransactionManager transactions;

    public Registration register(String email, String password, String invitation) {
        if (invitation == null || invitation.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is required");
        return verification.nested(() -> new TransactionTemplate(transactions).execute(status -> provision(email, password, invitation)));
    }

    // The identity shares the redemption transaction, so a failed redemption leaves no identity behind.
    private Registration provision(String email, String password, String invitation) {
        var preview = invitations.preview(invitation);
        AccountInvitationService.requireRecipientEmail(preview.email(), email);
        verification.prepareIdentity(email);
        String subject;
        try {
            subject = identities.createUser(email, password);
        }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new RegistrationValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists. Sign in instead.");
            }
            throw exception;
        }
        identities.markEmailVerified(subject, email);
        return new Registration(invitations.redeem(tokens.hash(invitation), email, subject), subject);
    }
}
