package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffOrderView(
        UUID id,
        UUID locationId,
        UUID trackingReference,
        long number,
        String label,
        @NonNull List<OrderItem> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static StaffOrderView from(CustomerOrder order) {
        return new StaffOrderView(
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
