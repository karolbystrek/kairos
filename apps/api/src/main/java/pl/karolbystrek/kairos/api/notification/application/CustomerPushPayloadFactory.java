package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushPayload;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class CustomerPushPayloadFactory {

    private final ObjectMapper objectMapper;

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
