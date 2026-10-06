package pl.karolbystrek.kairos.api.location.application.exception;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class LocationConflictException extends RuntimeException {

    private final Reason reason;

    public LocationConflictException(@NonNull Reason reason, @NonNull String message) {
        super(message);
        this.reason = reason;
    }

    public LocationConflictException(
        @NonNull Reason reason,
        @NonNull String message,
        Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        NAME_CONFLICT,
        ACTIVE_ORDERS,
        ENABLED_DELETE,
        LAST_LOCATION
    }
}
