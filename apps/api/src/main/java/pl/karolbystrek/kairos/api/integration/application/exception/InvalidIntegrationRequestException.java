package pl.karolbystrek.kairos.api.integration.application.exception;

public class InvalidIntegrationRequestException extends RuntimeException {

    public InvalidIntegrationRequestException(String message) {
        super(message);
    }

    public InvalidIntegrationRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
