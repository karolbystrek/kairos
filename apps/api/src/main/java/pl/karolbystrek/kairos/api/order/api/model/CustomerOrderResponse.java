package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;

public record CustomerOrderResponse(
	String label,
	OrderStatus status,
	Instant updatedAt
) {
}
