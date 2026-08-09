package pl.karolbystrek.kairos.api.location.application.exception;

public class InvalidLocationRequestException extends RuntimeException {

    public InvalidLocationRequestException(String message) {
        super(message);
    }

    public InvalidLocationRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
