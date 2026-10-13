package pl.karolbystrek.kairos.api.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.NonNull;

@Embeddable
public record OrderItem(
        @Column(name = "name", nullable = false, length = 60) @NonNull String name,
        @Column(name = "quantity", nullable = false) int quantity
) {
}
