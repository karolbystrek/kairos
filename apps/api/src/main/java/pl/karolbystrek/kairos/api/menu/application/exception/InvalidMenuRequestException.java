package pl.karolbystrek.kairos.api.menu.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class InvalidMenuRequestException extends ProblemException {

    public InvalidMenuRequestException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
