package pl.karolbystrek.kairos.api.authentication.application.exception;

public class InvalidLoginException extends RuntimeException {

    public InvalidLoginException() {
        super("Invalid username or password");
    }
}
