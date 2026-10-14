package pl.karolbystrek.kairos.api.menu.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class MenuNotFoundException extends ProblemException {

    public MenuNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
