package pl.karolbystrek.kairos.api.order.domain;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(OrderStatus current, OrderStatus target) {
        super("Order cannot transition from %s to %s".formatted(current, target));
    }
}
