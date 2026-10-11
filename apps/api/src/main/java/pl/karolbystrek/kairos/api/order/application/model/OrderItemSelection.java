package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;

public record OrderItemSelection(@NonNull String name, int quantity) {
}
