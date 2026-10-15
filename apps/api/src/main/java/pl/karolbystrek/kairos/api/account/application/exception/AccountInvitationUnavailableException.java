package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

import java.net.URI;
import java.util.Locale;

public class AccountInvitationUnavailableException extends ProblemException {

    private final Reason reason;

    public AccountInvitationUnavailableException(Reason reason) {
        super(HttpStatus.GONE, switch (reason) {
            case EXPIRED -> "This invitation has expired";
            case REVOKED -> "This invitation has been revoked";
            case REDEEMED -> "This invitation has already been used";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public URI getType() {
        return URI.create("urn:kairos:problem:account-invitation-" + reason.name().toLowerCase(Locale.ROOT));
    }

    public enum Reason {
        EXPIRED,
        REVOKED,
        REDEEMED
    }
}
