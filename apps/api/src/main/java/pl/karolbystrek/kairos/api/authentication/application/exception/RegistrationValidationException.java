package pl.karolbystrek.kairos.api.authentication.application.exception;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class RegistrationValidationException extends ResponseStatusException {
    private final String field;

    public RegistrationValidationException(HttpStatus status, @NonNull String field, @NonNull String message) {
        super(status, message);
        this.field = field;
    }
}
