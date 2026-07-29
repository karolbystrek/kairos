package pl.karolbystrek.kairos.api.notification.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerPushPayload(
        int version,
        @NonNull UUID eventId,
        @NonNull UUID trackingReference,
        @NonNull OrderStatus status,
        @NonNull Instant transitionedAt,
        @NonNull String orderUrl
) {

    public static final int CURRENT_VERSION = 1;
}
