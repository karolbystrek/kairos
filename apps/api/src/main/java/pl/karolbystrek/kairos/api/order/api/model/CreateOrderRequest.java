package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import pl.karolbystrek.kairos.api.order.application.model.OrderItemSelection;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Location is required")
        UUID locationId,

        @Size(min = 1, message = "Custom label must not be blank")
        @CodePointLength(max = 32, message = "Custom label must contain at most 32 characters")
        @Pattern(
                regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$",
                message = "Custom label must be single-line text without control characters"
        )
        String label,

        @Size(max = 100, message = "An order can contain at most 100 menu items")
        List<@NotNull(message = "Menu item is required") @Valid OrderItemRequest> items
) {
    public CreateOrderRequest {
        label = label == null ? null : label.strip();
    }

    public List<OrderItemSelection> itemSelections() {
        return items == null
                ? List.of()
                : items.stream().map(OrderItemRequest::toSelection).toList();
    }
}
