package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.OrderMeasurementRepository;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.ReviewInvitationProperties;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreation;
import pl.karolbystrek.kairos.api.order.application.model.OrderInitiator;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.port.OrderEventOutbox;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderHistory;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final CustomerOrderRepository orderRepository;
    private final OrderHistoryRepository historyRepository;
    private final OrderEventOutbox eventOutbox;
    private final OrderMeasurementRepository measurements;
    private final ApplicationEventPublisher eventPublisher;
    private final ReviewInvitationProperties reviewProperties;

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
        return persist(location, label, initiator, externalCreation, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerOrder createWithItems(
            Location location,
            String itemSummary,
            OrderInitiator initiator,
            Instant now
    ) {
        var label = nextAutomaticLabelNumber(location.getId(), now) + " · " + itemSummary;
        if (label.codePointCount(0, label.length()) > CustomerOrder.MAXIMUM_LABEL_LENGTH) {
            throw new InvalidOrderRequestException(
                    "The selected menu items do not fit in an order label; choose fewer items"
            );
        }
        return persist(location, label, initiator, null, now);
    }

    private CustomerOrder persist(
            Location location,
            String label,
            OrderInitiator initiator,
            ExternalOrderCreation externalCreation,
            Instant now
    ) {
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
        var eventId = eventOutbox.recordCreated(order, now);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(eventId, order));
        measurements.record(order, now, null);
        return order;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateStatus(
            CustomerOrder order,
            OrderStatus target,
            OrderInitiator initiator,
            Instant now
    ) {
        var durationStart = target == OrderStatus.READY ? order.getCreatedAt() : order.getUpdatedAt();
        if (!order.transitionTo(target, now)) {
            return false;
        }

        if (target == OrderStatus.COMPLETED) {
            order.scheduleReview(now.plus(reviewProperties.delay()));
        }
        // A backwards clock still counts the transition, but cannot supply a valid duration.
        Long durationMs = (target == OrderStatus.READY || target == OrderStatus.COMPLETED)
                && !now.isBefore(durationStart) ? Duration.between(durationStart, now).toMillis() : null;
        measurements.record(order, now, durationMs);
        historyRepository.save(historyFor(order, target, now, initiator));
        var eventId = eventOutbox.recordStatusChanged(order, now);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(eventId, order));
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
                    initiator.apiKeyId()
            );
            case SYSTEM -> OrderHistory.recordBySystem(order, status, now);
        };
    }

    private long nextAutomaticLabelNumber(UUID locationId, Instant now) {
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
