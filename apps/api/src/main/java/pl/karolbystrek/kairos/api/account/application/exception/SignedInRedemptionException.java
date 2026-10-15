package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class SignedInRedemptionException extends ProblemException {

    public SignedInRedemptionException() {
        super(HttpStatus.FORBIDDEN, "Sign out before accepting an invitation");
    }
}
