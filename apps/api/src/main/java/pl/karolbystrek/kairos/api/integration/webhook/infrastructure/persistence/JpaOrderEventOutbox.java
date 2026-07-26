package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.OrderCloudEventFactory;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookOutboxEvent;
import pl.karolbystrek.kairos.api.order.application.port.OrderEventOutbox;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaOrderEventOutbox implements OrderEventOutbox {

    private final WebhookOutboxEventRepository outboxRepository;
    private final OrderCloudEventFactory cloudEventFactory;

    @Override
    public void recordCreated(CustomerOrder order, Instant occurredAt) {
        record(order, WebhookEventType.ORDER_CREATED, occurredAt);
    }

    @Override
    public void recordStatusChanged(CustomerOrder order, Instant occurredAt) {
        record(order, WebhookEventType.forStatus(order.getStatus()), occurredAt);
    }

    private void record(
            CustomerOrder order,
            WebhookEventType eventType,
            Instant occurredAt
    ) {
        var eventId = UUID.randomUUID();
        var location = order.getLocation();
        var payload = cloudEventFactory.create(eventId, eventType, order, occurredAt);
        outboxRepository.save(WebhookOutboxEvent.create(
                eventId,
                order.getId(),
                location.getTenantId(),
                location.getId(),
                eventType,
                occurredAt,
                payload,
                occurredAt
        ));
    }
}
