package pl.karolbystrek.kairos.api.location.application.exception;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.problem.ProblemException;

import java.net.URI;

@Getter
public class LocationConflictException extends ProblemException {

    private final Reason reason;

    public LocationConflictException(@NonNull Reason reason, @NonNull String message) {
        super(HttpStatus.CONFLICT, message);
        this.reason = reason;
    }

    public LocationConflictException(
        @NonNull Reason reason,
        @NonNull String message,
        Throwable cause
    ) {
        super(HttpStatus.CONFLICT, message, cause);
        this.reason = reason;
    }

    @Override
    public URI getType() {
        return URI.create(switch (reason) {
            case ACTIVE_ORDERS -> "urn:kairos:problem:location-active-orders";
            case LAST_LOCATION -> "urn:kairos:problem:location-last-location";
            default -> "urn:kairos:problem:location-conflict";
        });
    }

    public enum Reason {
        NAME_CONFLICT,
        ACTIVE_ORDERS,
        ENABLED_DELETE,
        LAST_LOCATION
    }
}
