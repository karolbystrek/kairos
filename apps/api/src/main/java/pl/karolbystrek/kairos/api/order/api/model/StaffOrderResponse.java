package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record StaffOrderResponse(
        UUID id,
        UUID locationId,
        UUID trackingReference,
        String label,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static StaffOrderResponse from(StaffOrderView order) {
        return new StaffOrderResponse(
                order.id(),
                order.locationId(),
                order.trackingReference(),
                order.label(),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
