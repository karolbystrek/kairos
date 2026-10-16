package pl.karolbystrek.kairos.api.authentication.application.exception;

import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

import java.net.URI;

public class SignInThrottledException extends ProblemException {

    public SignInThrottledException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Try again later.");
    }

    @Override
    public URI getType() {
        return URI.create("urn:kairos:problem:sign-in-throttled");
    }
}
