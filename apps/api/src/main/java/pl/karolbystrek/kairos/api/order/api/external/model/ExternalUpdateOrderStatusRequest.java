package pl.karolbystrek.kairos.api.order.api.external.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

public record ExternalUpdateOrderStatusRequest(
        @NotNull(message = "Desired order status is required")
        OrderStatus status
) {
}
