package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID locationId,
        UUID trackingReference,
        long number,
        String label,
        List<OrderItemResponse> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(OrderView order) {
        return new OrderResponse(
                order.id(),
                order.locationId(),
                order.trackingReference(),
                order.number(),
                order.label(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
