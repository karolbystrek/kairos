package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ExternalOrderSnapshot;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.StructuredOrderCloudEvent;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCloudEventFactory {

    private static final String SPEC_VERSION = "1.0";
    private static final String SOURCE = "urn:kairos:orders";
    private static final String CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;

    public String create(
            UUID eventId,
            OrderEventType eventType,
            CustomerOrder order,
            Instant occurredAt
    ) {
        var event = new StructuredOrderCloudEvent(
                SPEC_VERSION,
                eventId,
                SOURCE,
                eventType.cloudEventType(),
                "orders/" + order.getId(),
                occurredAt,
                CONTENT_TYPE,
                ExternalOrderSnapshot.from(order)
        );
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize an order webhook event", exception);
        }
    }
}
