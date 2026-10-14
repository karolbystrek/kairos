package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class InvalidAccountRequestException extends ProblemException {

    public InvalidAccountRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
