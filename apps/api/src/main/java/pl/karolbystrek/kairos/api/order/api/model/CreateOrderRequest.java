package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
	@NotBlank(message = "Order label is required")
	@Size(max = 80, message = "Order label must not exceed 80 characters")
	String label
) {
}
