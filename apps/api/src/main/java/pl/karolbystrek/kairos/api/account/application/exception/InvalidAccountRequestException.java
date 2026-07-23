package pl.karolbystrek.kairos.api.account.application.exception;

public class InvalidAccountRequestException extends RuntimeException {

    public InvalidAccountRequestException(String message) {
        super(message);
    }
}
