package pl.karolbystrek.kairos.api.integration.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class IntegrationAccessDeniedException extends ProblemException {

    public IntegrationAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
