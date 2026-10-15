package pl.karolbystrek.kairos.api.account.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class StaffAccessDeniedException extends ProblemException {

    public StaffAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
