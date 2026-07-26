package pl.karolbystrek.kairos.api.integration.application.exception;

public class IntegrationConflictException extends RuntimeException {

    public IntegrationConflictException(String message) {
        super(message);
    }

    public IntegrationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
