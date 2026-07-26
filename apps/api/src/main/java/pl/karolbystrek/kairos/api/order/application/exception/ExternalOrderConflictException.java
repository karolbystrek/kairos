package pl.karolbystrek.kairos.api.order.application.exception;

public class ExternalOrderConflictException extends RuntimeException {

    public ExternalOrderConflictException(String message) {
        super(message);
    }
}
