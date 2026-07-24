package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderHistory;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final LocationRepository locationRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderHistoryRepository historyRepository;
    private final StaffAccessService staffAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StaffOrderView> listOrders(StaffPrincipal principal, UUID locationId) {
        var access = staffAccessService.resolve(principal);
        requireAccessibleLocation(access, locationId);
        return orderRepository.findAllByLocationIdAndStatusInOrderByCreatedAtDesc(
                        locationId,
                        OrderStatus.activeStatuses()
                ).stream()
                .map(StaffOrderView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffOrderView> listTenantOrders(StaffPrincipal principal) {
        var access = staffAccessService.resolve(principal);
        if (!access.isTenantAdmin()) {
            throw new StaffAccessDeniedException("Tenant-wide order access requires an administrator");
        }
        return orderRepository.findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(
                        access.tenantId(),
                        OrderStatus.activeStatuses()
                ).stream()
                .map(StaffOrderView::from)
                .toList();
    }

    @Transactional
    public StaffOrderView createOrder(StaffPrincipal principal, UUID locationId, String customLabel) {
        var access = staffAccessService.resolveForUpdate(principal);
        var location = requireAccessibleLocationForUpdate(access, locationId);
        var now = clock.instant();
        var label = customLabel == null
                ? Long.toString(nextAutomaticLabelNumber(locationId, now))
                : customLabel;
        var order = orderRepository.save(CustomerOrder.create(
                location,
                label,
                now
        ));
        historyRepository.save(OrderHistory.recordByUser(order, order.getStatus(), now, access.accountId()));
        return StaffOrderView.from(order);
    }

    @Transactional
    public StaffOrderView updateStatus(StaffPrincipal principal, UUID orderId, OrderStatus target) {
        var access = staffAccessService.resolveForUpdate(principal);
        var order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order was not found"));
        access.requireLocationAccess(
                order.getLocation().getTenantId(),
                order.getLocation().getId()
        );
        var now = clock.instant();
        order.transitionTo(target, now);
        historyRepository.save(OrderHistory.recordByUser(order, target, now, access.accountId()));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getTrackingReference(),
                target,
                now
        ));
        return StaffOrderView.from(order);
    }

    @Transactional(readOnly = true)
    public TrackedOrderView findTrackedOrder(UUID trackingReference) {
        var order = orderRepository.findByTrackingReference(trackingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked order was not found"));
        return TrackedOrderView.from(order);
    }

    private Location requireLocation(UUID locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location was not found"));
    }

    private Location requireAccessibleLocation(StaffAccessContext access, UUID locationId) {
        var location = requireLocation(locationId);
        access.requireLocationAccess(location.getTenantId(), location.getId());
        return location;
    }

    private Location requireAccessibleLocationForUpdate(StaffAccessContext access, UUID locationId) {
        var location = locationRepository.findForUpdateById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        return location;
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
