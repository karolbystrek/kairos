package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

public class InvalidWebhookDestinationException extends RuntimeException {

    public InvalidWebhookDestinationException(String message) {
        super(message);
    }

    public InvalidWebhookDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
