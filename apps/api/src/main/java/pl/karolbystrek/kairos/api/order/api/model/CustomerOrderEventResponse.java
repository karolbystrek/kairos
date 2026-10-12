package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.model.ReviewInvitationView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerOrderEventResponse(UUID eventId, UUID trackingReference, String label,
                                         OrderStatus status, Instant updatedAt,
                                         ReviewInvitationView reviewInvitation) {
    public static CustomerOrderEventResponse from(OrderStatusChangedEvent event) {
        return new CustomerOrderEventResponse(event.eventId(), event.trackingReference(), event.label(),
                event.status(), event.updatedAt(), event.reviewInvitation());
    }
}
