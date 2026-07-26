package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record ExternalOrderView(
        @NonNull UUID id,
        @NonNull UUID locationId,
        @NonNull UUID trackingReference,
        @NonNull String label,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static ExternalOrderView from(CustomerOrder order) {
        return new ExternalOrderView(
                order.getId(),
                order.getLocation().getId(),
                order.getTrackingReference(),
                order.getLabel(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
