package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
        @NonNull UUID eventId,
        @NonNull UUID trackingReference,
        @NonNull OrderStatus status,
        @NonNull Instant updatedAt,
        long number,
        String label,
        ReviewInvitationView reviewInvitation,
        UUID tenantId,
        UUID locationId
) {
    public static OrderStatusChangedEvent from(UUID eventId, CustomerOrder order) {
        var tracked = TrackedOrderView.from(order);
        return new OrderStatusChangedEvent(eventId, order.getTrackingReference(), order.getStatus(),
                order.getUpdatedAt(), tracked.number(), tracked.label(), tracked.reviewInvitation(),
                order.getLocation().getTenantId(), order.getLocation().getId());
    }

    public static OrderStatusChangedEvent snapshot(UUID reference, TrackedOrderView order) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), reference, order.status(), order.updatedAt(),
                order.number(), order.label(), order.reviewInvitation(), null, null);
    }
}
