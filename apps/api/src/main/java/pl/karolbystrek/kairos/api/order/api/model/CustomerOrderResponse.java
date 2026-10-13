package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.order.application.model.ReviewInvitationView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;

public record CustomerOrderResponse(
        long number,
        String label,
        OrderStatus status,
        Instant updatedAt,
        ReviewInvitationView reviewInvitation
) {
    public static CustomerOrderResponse from(TrackedOrderView order) {
        return new CustomerOrderResponse(order.number(), order.label(), order.status(), order.updatedAt(), order.reviewInvitation());
    }
}
