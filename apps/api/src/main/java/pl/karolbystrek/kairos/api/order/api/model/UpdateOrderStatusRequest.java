package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
