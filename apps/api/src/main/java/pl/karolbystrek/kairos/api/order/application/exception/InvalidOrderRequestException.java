package pl.karolbystrek.kairos.api.order.application.exception;

public class InvalidOrderRequestException extends RuntimeException {

    public InvalidOrderRequestException(String message) {
        super(message);
    }
}
