package pl.karolbystrek.kairos.api.integration.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class IntegrationConflictException extends ProblemException {

    public IntegrationConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public IntegrationConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, message, cause);
    }
}
