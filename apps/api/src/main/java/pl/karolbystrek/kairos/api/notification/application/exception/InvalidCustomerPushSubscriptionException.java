package pl.karolbystrek.kairos.api.notification.application.exception;

public class InvalidCustomerPushSubscriptionException extends RuntimeException {

    public InvalidCustomerPushSubscriptionException(String message) {
        super(message);
    }

    public InvalidCustomerPushSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
