package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExternalOrderSnapshot(
        @NonNull UUID id,
        @NonNull UUID locationId,
        long number,
        String label,
        @NonNull List<OrderItem> items,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static ExternalOrderSnapshot from(CustomerOrder order) {
        return new ExternalOrderSnapshot(
                order.getId(),
                order.getLocation().getId(),
                order.getNumber(),
                order.getLabel(),
                order.getItems(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
