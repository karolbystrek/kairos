package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
        @NonNull UUID id,
        @NonNull UUID locationId,
        @NonNull UUID trackingReference,
        long number,
        String label,
        @NonNull List<OrderItem> items,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static OrderView from(CustomerOrder order) {
        return new OrderView(
                order.getId(),
                order.getLocation().getId(),
                order.getTrackingReference(),
                order.getNumber(),
                order.getLabel(),
                order.getItems(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
