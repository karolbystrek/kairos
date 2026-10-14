package pl.karolbystrek.kairos.api.integration.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class InvalidIntegrationRequestException extends ProblemException {

    public InvalidIntegrationRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public InvalidIntegrationRequestException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
