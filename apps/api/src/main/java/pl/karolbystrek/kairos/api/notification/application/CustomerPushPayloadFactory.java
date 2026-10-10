package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushPayload;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerReviewPushPayload;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CustomerPushPayloadFactory {

    private final ObjectMapper objectMapper;

    String createReview(OrderOutboxEvent event, Instant dueAt) {
        var reviewEventId = UUID.nameUUIDFromBytes(("review:" + event.getId()).getBytes(StandardCharsets.UTF_8));
        return objectMapper.writeValueAsString(new CustomerReviewPushPayload(2, "REVIEW", reviewEventId,
                event.getTrackingReference(), dueAt, "/?review=" + event.getTrackingReference()));
    }

    String create(OrderOutboxEvent event) {
        return objectMapper.writeValueAsString(new CustomerPushPayload(
                CustomerPushPayload.CURRENT_VERSION,
                event.getId(),
                event.getTrackingReference(),
                event.getStatus(),
                event.getOccurredAt(),
                "/orders/" + event.getTrackingReference()
        ));
    }
}
