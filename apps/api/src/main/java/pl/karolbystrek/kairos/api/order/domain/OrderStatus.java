package pl.karolbystrek.kairos.api.order.domain;

import java.util.Set;

public enum OrderStatus {
    IN_PREPARATION,
    READY,
    COMPLETED,
    CANCELED;

    private static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(IN_PREPARATION, READY);

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case IN_PREPARATION -> target == READY || target == CANCELED;
            case READY -> target == COMPLETED || target == CANCELED;
            case COMPLETED, CANCELED -> false;
        };
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(this);
    }

    public boolean isTerminal() {
        return !isActive();
    }

    public static Set<OrderStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }
}
