package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class AccountNotFoundException extends ProblemException {

    public AccountNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
