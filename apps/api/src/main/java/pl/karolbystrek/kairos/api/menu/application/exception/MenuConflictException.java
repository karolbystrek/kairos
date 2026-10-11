package pl.karolbystrek.kairos.api.menu.application.exception;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class MenuConflictException extends RuntimeException {

    private final Reason reason;

    public MenuConflictException(@NonNull Reason reason, @NonNull String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        NAME_CONFLICT,
        IN_USE
    }
}
