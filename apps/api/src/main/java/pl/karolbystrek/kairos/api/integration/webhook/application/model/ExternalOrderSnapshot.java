package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record ExternalOrderSnapshot(
        @NonNull UUID id,
        @NonNull UUID locationId,
        @NonNull String label,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static ExternalOrderSnapshot from(CustomerOrder order) {
        return new ExternalOrderSnapshot(
                order.getId(),
                order.getLocation().getId(),
                order.getLabel(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
