package pl.karolbystrek.kairos.api.location.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

public class LocationNotFoundException extends ProblemException {

    public LocationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Location was not found");
    }
}
