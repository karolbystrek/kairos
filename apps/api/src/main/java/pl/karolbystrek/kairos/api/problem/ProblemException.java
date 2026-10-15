package pl.karolbystrek.kairos.api.problem;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.net.URI;

/** Application failure rendered by {@link ApiExceptionHandler} as a problem detail with its status and message. */
@Getter
public abstract class ProblemException extends RuntimeException {

    private final HttpStatus status;

    protected ProblemException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ProblemException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** Problem type URN, or {@code null} for the default {@code about:blank}. */
    public URI getType() {
        return null;
    }
}
