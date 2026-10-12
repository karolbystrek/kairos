package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.application.model.ReviewInvitationView;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusRedisMessage(
        int version,
        @NonNull UUID eventId,
        @NonNull UUID trackingReference,
        @NonNull OrderStatus status,
        @NonNull Instant updatedAt,
        @NonNull String label,
        ReviewInvitationView reviewInvitation,
        @NonNull UUID tenantId,
        @NonNull UUID locationId
) {
    public static final int CURRENT_VERSION = 1;

    public static OrderStatusRedisMessage from(OrderStatusChangedEvent event) {
        return new OrderStatusRedisMessage(
                CURRENT_VERSION,
                event.eventId(),
                event.trackingReference(),
                event.status(),
                event.updatedAt(), event.label(), event.reviewInvitation(), event.tenantId(), event.locationId()
        );
    }

    public boolean isSupported() {
        return version == CURRENT_VERSION;
    }

    public OrderStatusChangedEvent toApplicationEvent() {
        return new OrderStatusChangedEvent(eventId, trackingReference, status, updatedAt, label, reviewInvitation, tenantId, locationId);
    }
}
