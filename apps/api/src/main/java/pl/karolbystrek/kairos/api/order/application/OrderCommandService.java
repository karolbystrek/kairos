package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.OrderMeasurementRepository;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.menu.application.MenuService;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.ReviewInvitationProperties;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreation;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.port.OrderEventOutbox;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final CustomerOrderRepository orderRepository;
    private final OrderEventOutbox eventOutbox;
    private final OrderMeasurementRepository measurements;
    private final ApplicationEventPublisher eventPublisher;
    private final ReviewInvitationProperties reviewProperties;
    private final MenuService menuService;

    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerOrder create(
            Location location,
            String label,
            List<OrderItem> items,
            ExternalOrderCreation externalCreation,
            Instant now
    ) {
        requireMenuItems(location, items);
        var businessDate = LocalDate.ofInstant(now, ZoneId.of(location.getTimeZone()));
        var number = orderRepository.allocateNumber(location.getId(), businessDate);
        var order = externalCreation == null
                ? CustomerOrder.create(location, number, businessDate, label, items, now)
                : CustomerOrder.createByIntegration(
                        location,
                        number,
                        businessDate,
                        label,
                        items,
                        now,
                        externalCreation.integrationId(),
                        externalCreation.idempotencyKey(),
                        externalCreation.requestFingerprint()
                );
        orderRepository.save(order);
        var eventId = eventOutbox.recordCreated(order, now);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(eventId, order));
        measurements.record(order, now, null);
        return order;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateStatus(
            CustomerOrder order,
            OrderStatus target,
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
        var eventId = eventOutbox.recordStatusChanged(order, now);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(eventId, order));
        return true;
    }

    /** Expiry is recorded like any transition but adds no analytics measurement and no review invitation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expire(CustomerOrder order, Instant now) {
        if (!order.expire(now)) {
            return false;
        }
        var eventId = eventOutbox.recordStatusChanged(order, now);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(eventId, order));
        return true;
    }

    private void requireMenuItems(Location location, List<OrderItem> items) {
        if (items.isEmpty()) {
            return;
        }
        var menuItems = menuService.menuItems(location.getId());
        if (menuItems.isEmpty()) {
            throw new InvalidOrderRequestException("This location has no menu to choose items from");
        }
        var names = new HashSet<String>();
        for (var item : items) {
            if (!menuItems.contains(item.name())) {
                throw new InvalidOrderRequestException("The menu has changed; reload it and choose the items again");
            }
            if (!names.add(item.name())) {
                throw new InvalidOrderRequestException("Each menu item can be chosen only once");
            }
        }
    }
}
