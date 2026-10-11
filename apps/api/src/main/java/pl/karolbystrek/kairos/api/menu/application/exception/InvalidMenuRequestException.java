package pl.karolbystrek.kairos.api.menu.application.exception;

public class InvalidMenuRequestException extends RuntimeException {

    public InvalidMenuRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
