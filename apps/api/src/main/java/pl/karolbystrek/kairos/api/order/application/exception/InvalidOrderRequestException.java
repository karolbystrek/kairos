package pl.karolbystrek.kairos.api.order.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class InvalidOrderRequestException extends ProblemException {

    public InvalidOrderRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
