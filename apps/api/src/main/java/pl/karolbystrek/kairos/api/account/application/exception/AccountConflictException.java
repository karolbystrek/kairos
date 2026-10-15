package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class AccountConflictException extends ProblemException {

    public AccountConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public AccountConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, message, cause);
    }
}
