package pl.karolbystrek.kairos.api.order.api.external.model;

import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record ExternalOrderResponse(
        UUID id,
        UUID locationId,
        UUID trackingReference,
        String label,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ExternalOrderResponse from(ExternalOrderView order) {
        return new ExternalOrderResponse(
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
