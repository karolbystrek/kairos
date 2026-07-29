package pl.karolbystrek.kairos.api.notification.domain;

public enum CustomerPushDeliveryStatus {
    PENDING,
    PROCESSING,
    ACCEPTED,
    DEAD_LETTERED,
    EXPIRED,
    SUPERSEDED,
    CANCELED;

    public boolean isTerminal() {
        return this != PENDING && this != PROCESSING;
    }
}
