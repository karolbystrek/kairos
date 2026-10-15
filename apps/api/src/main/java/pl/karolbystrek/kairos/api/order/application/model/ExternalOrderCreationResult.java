package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;

public record ExternalOrderCreationResult(
        @NonNull OrderView order,
        boolean replayed
) {
}
