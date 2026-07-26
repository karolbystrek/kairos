package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreation;
import pl.karolbystrek.kairos.api.order.application.model.OrderInitiator;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.port.OrderEventOutbox;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderHistory;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final CustomerOrderRepository orderRepository;
    private final OrderHistoryRepository historyRepository;
    private final OrderEventOutbox eventOutbox;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerOrder create(
            Location location,
            String customLabel,
            OrderInitiator initiator,
            ExternalOrderCreation externalCreation,
            Instant now
    ) {
        var label = customLabel == null
                ? Long.toString(nextAutomaticLabelNumber(location.getId(), now))
                : customLabel;
        var order = externalCreation == null
                ? CustomerOrder.create(location, label, now)
                : CustomerOrder.createByIntegration(
                        location,
                        label,
                        now,
                        externalCreation.integrationId(),
                        externalCreation.idempotencyKey(),
                        externalCreation.requestFingerprint()
                );
        orderRepository.save(order);
        historyRepository.save(historyFor(order, order.getStatus(), now, initiator));
        eventOutbox.recordCreated(order, now);
        return order;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateStatus(
            CustomerOrder order,
            OrderStatus target,
            OrderInitiator initiator,
            Instant now
    ) {
        if (!order.transitionTo(target, now)) {
            return false;
        }

        historyRepository.save(historyFor(order, target, now, initiator));
        eventOutbox.recordStatusChanged(order, now);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getTrackingReference(),
                target,
                now
        ));
        return true;
    }

    private OrderHistory historyFor(
            CustomerOrder order,
            OrderStatus status,
            Instant now,
            OrderInitiator initiator
    ) {
        return switch (initiator.type()) {
            case USER -> OrderHistory.recordByUser(
                    order,
                    status,
                    now,
                    initiator.initiatorId()
            );
            case INTEGRATION -> OrderHistory.recordByIntegration(
                    order,
                    status,
                    now,
                    initiator.initiatorId(),
                    initiator.apiKeyId(),
                    initiator.apiKeyVersionId()
            );
            case SYSTEM -> OrderHistory.recordBySystem(order, status, now);
        };
    }

    private long nextAutomaticLabelNumber(java.util.UUID locationId, Instant now) {
        var utcDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var startInclusive = utcDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        var endExclusive = utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var existingOrderCount =
                orderRepository.countByLocationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        locationId,
                        startInclusive,
                        endExclusive
                );
        return Math.addExact(existingOrderCount, 1);
    }
}
