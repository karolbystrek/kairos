package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.application.OrderCloudEventFactory;
import pl.karolbystrek.kairos.api.order.application.port.OrderEventOutbox;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderOutboxEventRepository;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaOrderEventOutbox implements OrderEventOutbox {

    private final OrderOutboxEventRepository outboxRepository;
    private final OrderCloudEventFactory cloudEventFactory;

    @Override
    public UUID recordCreated(CustomerOrder order, Instant occurredAt) {
        return record(order, OrderEventType.ORDER_CREATED, occurredAt);
    }

    @Override
    public UUID recordStatusChanged(CustomerOrder order, Instant occurredAt) {
        return record(order, OrderEventType.forStatus(order.getStatus()), occurredAt);
    }

    private UUID record(
            CustomerOrder order,
            OrderEventType eventType,
            Instant occurredAt
    ) {
        var eventId = UUID.randomUUID();
        var location = order.getLocation();
        var payload = cloudEventFactory.create(eventId, eventType, order, occurredAt);
        outboxRepository.saveAndFlush(OrderOutboxEvent.create(
                eventId,
                order.getId(),
                location.getTenantId(),
                location.getId(),
                order.getTrackingReference(),
                eventType,
                order.getStatus(),
                occurredAt,
                payload,
                occurredAt
        ));
        return eventId;
    }
}
