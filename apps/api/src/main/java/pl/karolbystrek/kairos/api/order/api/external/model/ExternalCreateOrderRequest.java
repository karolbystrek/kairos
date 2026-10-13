package pl.karolbystrek.kairos.api.order.api.external.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import pl.karolbystrek.kairos.api.order.api.model.OrderItemRequest;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;

import java.util.List;
import java.util.UUID;

public record ExternalCreateOrderRequest(
        @NotNull(message = "Location is required")
        UUID locationId,

        @CodePointLength(max = 80, message = "Label must contain at most 80 characters")
        @Pattern(
                regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$",
                message = "Label must be single-line text without control characters"
        )
        String label,

        @Size(max = 100, message = "An order can contain at most 100 menu items")
        List<@NotNull(message = "Menu item is required") @Valid OrderItemRequest> items
) {

    public ExternalCreateOrderRequest {
        label = label == null || label.isBlank() ? null : label.strip();
    }

    public List<OrderItem> orderItems() {
        return items == null ? List.of() : items.stream().map(OrderItemRequest::toItem).toList();
    }
}
