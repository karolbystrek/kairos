package pl.karolbystrek.kairos.api.order.domain;

public enum OrderStatus {
    CREATED,
    IN_PREPARATION,
    READY,
    COMPLETED,
    CANCELED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == IN_PREPARATION || target == CANCELED;
            case IN_PREPARATION -> target == READY || target == CANCELED;
            case READY -> target == COMPLETED || target == CANCELED;
            case COMPLETED, CANCELED -> false;
        };
    }
}
