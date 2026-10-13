package pl.karolbystrek.kairos.api.order.api.model;

import pl.karolbystrek.kairos.api.order.domain.OrderItem;

public record OrderItemResponse(String name, int quantity) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.name(), item.quantity());
    }
}
