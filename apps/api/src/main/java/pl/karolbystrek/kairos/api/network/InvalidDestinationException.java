package pl.karolbystrek.kairos.api.network;

public class InvalidDestinationException extends RuntimeException {

    public InvalidDestinationException(String message) {
        super(message);
    }

    public InvalidDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
