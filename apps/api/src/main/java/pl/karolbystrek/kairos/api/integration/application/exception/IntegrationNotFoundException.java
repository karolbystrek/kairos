package pl.karolbystrek.kairos.api.integration.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class IntegrationNotFoundException extends ProblemException {

    public IntegrationNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
