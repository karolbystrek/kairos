package pl.karolbystrek.kairos.api.authentication.application.exception;

public class InvalidRefreshCredentialException extends RuntimeException {

    public InvalidRefreshCredentialException() {
        super("The refresh credential is invalid or expired");
    }
}
