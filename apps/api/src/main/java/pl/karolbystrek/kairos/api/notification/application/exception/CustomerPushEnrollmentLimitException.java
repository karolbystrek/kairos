package pl.karolbystrek.kairos.api.notification.application.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CustomerPushEnrollmentLimitException extends RuntimeException {

    private final UUID trackingReference;

    public CustomerPushEnrollmentLimitException(UUID trackingReference) {
        super("This order already has the maximum number of notification subscribers");
        this.trackingReference = trackingReference;
    }
}
