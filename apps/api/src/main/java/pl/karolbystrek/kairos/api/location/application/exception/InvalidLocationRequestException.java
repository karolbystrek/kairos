package pl.karolbystrek.kairos.api.location.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class InvalidLocationRequestException extends ProblemException {

    public InvalidLocationRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public InvalidLocationRequestException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
