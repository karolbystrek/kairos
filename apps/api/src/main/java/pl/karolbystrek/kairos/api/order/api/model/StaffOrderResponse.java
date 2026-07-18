package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record StaffOrderResponse(
	UUID id,
	UUID locationId,
	UUID trackingReference,
	String label,
	OrderStatus status,
	Instant createdAt,
	Instant updatedAt
) {
}
