package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
        @NonNull UUID eventId,
        @NonNull UUID trackingReference,
        @NonNull OrderStatus status,
        @NonNull Instant updatedAt
) {
}
