package pl.karolbystrek.kairos.api.integration.application.exception;

public class IntegrationAccessDeniedException extends RuntimeException {

    public IntegrationAccessDeniedException(String message) {
        super(message);
    }
}
