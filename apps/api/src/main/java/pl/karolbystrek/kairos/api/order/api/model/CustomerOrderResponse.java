package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;

public record CustomerOrderResponse(
        String label,
        OrderStatus status,
        Instant updatedAt
) {
    public static CustomerOrderResponse from(TrackedOrderView order) {
        return new CustomerOrderResponse(order.label(), order.status(), order.updatedAt());
    }
}
