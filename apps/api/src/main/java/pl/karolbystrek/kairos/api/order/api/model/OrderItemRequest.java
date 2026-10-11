package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.order.application.model.OrderItemSelection;

public record OrderItemRequest(
        @NotBlank(message = "Menu item name is required")
        String name,

        @NotNull(message = "Menu item quantity is required")
        @Min(value = 1, message = "Menu item quantity must be between 1 and 99")
        @Max(value = 99, message = "Menu item quantity must be between 1 and 99")
        Integer quantity
) {
    public OrderItemSelection toSelection() {
        return new OrderItemSelection(name.strip(), quantity);
    }
}
