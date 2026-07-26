package pl.karolbystrek.kairos.api.integration.application.exception;

public class IntegrationNotFoundException extends RuntimeException {

    public IntegrationNotFoundException(String message) {
        super(message);
    }
}
