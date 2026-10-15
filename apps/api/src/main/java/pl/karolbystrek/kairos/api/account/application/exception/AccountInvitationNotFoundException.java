package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

import java.net.URI;

public class AccountInvitationNotFoundException extends ProblemException {

    public AccountInvitationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "This invitation is not valid");
    }

    @Override
    public URI getType() {
        return URI.create("urn:kairos:problem:account-invitation-invalid");
    }
}
