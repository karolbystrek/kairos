package pl.karolbystrek.kairos.api.order.api.external.model;

import pl.karolbystrek.kairos.api.order.api.model.OrderItemResponse;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExternalOrderResponse(
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

    public static ExternalOrderResponse from(ExternalOrderView order) {
        return new ExternalOrderResponse(
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
