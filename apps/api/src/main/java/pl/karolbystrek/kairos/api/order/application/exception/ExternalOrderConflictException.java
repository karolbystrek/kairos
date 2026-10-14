package pl.karolbystrek.kairos.api.order.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class ExternalOrderConflictException extends ProblemException {

    public ExternalOrderConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
