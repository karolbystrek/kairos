package pl.karolbystrek.kairos.api.order.application.model;

import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;

public record TrackedOrderView(
	String label,
	OrderStatus status,
	Instant updatedAt
) {
}
