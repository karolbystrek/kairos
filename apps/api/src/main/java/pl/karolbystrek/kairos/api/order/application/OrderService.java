package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.application.model.OrderInitiator;
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final LocationRepository locationRepository;
    private final CustomerOrderRepository orderRepository;
    private final StaffAccessService staffAccessService;
    private final OrderCommandService commandService;
    private final Clock clock;
    private final DatabaseAccessContext databaseAccess;

    /** The access part of {@link #listOrders} without the query, for re-checking open queue streams. */
    @Transactional(readOnly = true)
    public void requireQueueAccess(StaffPrincipal principal, UUID locationId) {
        var access = staffAccessService.resolve(principal);
        if (locationId != null) {
            requireAccessibleLocation(access, locationId);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderView> listOrders(
            StaffPrincipal principal,
            UUID locationId,
            OrderStatus status
    ) {
        var access = staffAccessService.resolve(principal);
        var statuses = status == null ? OrderStatus.activeStatuses() : activeStatusFilter(status);

        if (locationId != null) {
            requireAccessibleLocation(access, locationId);
        }
        var scopedLocationId = locationId != null ? locationId : access.locationId();
        var orders = scopedLocationId != null
                ? orderRepository.findAllByLocationIdAndStatusInOrderByCreatedAtDesc(scopedLocationId, statuses)
                : orderRepository.findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(access.tenantId(), statuses);
        return orders.stream()
                .map(OrderView::from)
                .toList();
    }

    @Transactional
    public OrderView createOrder(
            StaffPrincipal principal,
            UUID locationId,
            String label,
            List<OrderItem> items
    ) {
        var access = staffAccessService.resolveForUpdate(principal);
        var location = requireAccessibleLocationForUpdate(access, locationId);
        var initiator = OrderInitiator.user(access.accountId());
        return OrderView.from(commandService.create(location, label, items, initiator, null, clock.instant()));
    }

    @Transactional
    public OrderView updateStatus(StaffPrincipal principal, UUID orderId, OrderStatus target) {
        var access = staffAccessService.resolveForUpdate(principal);
        var order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order was not found"));
        access.requireLocationAccess(
                order.getLocation().getTenantId(),
                order.getLocation().getId()
        );
        var now = clock.instant();
        commandService.updateStatus(
                order,
                target,
                OrderInitiator.user(access.accountId()),
                now
        );
        return OrderView.from(order);
    }

    @Transactional(readOnly = true)
    public TrackedOrderView findTrackedOrder(UUID trackingReference) {
        databaseAccess.trackedOrders(List.of(trackingReference));
        var order = orderRepository.findByTrackingReference(trackingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked order was not found"));
        return TrackedOrderView.from(order);
    }

    // One authority-bound query for many streams; unknown references are absent from the result.
    @Transactional(readOnly = true)
    public Map<UUID, TrackedOrderView> findTrackedOrders(Collection<UUID> trackingReferences) {
        databaseAccess.trackedOrders(trackingReferences);
        return orderRepository.findAllByTrackingReferenceIn(trackingReferences).stream()
                .collect(Collectors.toMap(CustomerOrder::getTrackingReference, TrackedOrderView::from));
    }

    private Location requireLocation(UUID locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location was not found"));
    }

    private Location requireAccessibleLocation(StaffAccessContext access, UUID locationId) {
        access.requireLocationAccess(access.tenantId(), locationId);
        var location = requireLocation(locationId);
        access.requireLocationAccess(location.getTenantId(), location.getId());
        requireEnabledLocation(location);
        return location;
    }

    private Location requireAccessibleLocationForUpdate(StaffAccessContext access, UUID locationId) {
        access.requireLocationAccess(access.tenantId(), locationId);
        var location = locationRepository.findForUpdateById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location was not found"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        requireEnabledLocation(location);
        return location;
    }

    private static void requireEnabledLocation(Location location) {
        if (!location.isEnabled()) {
            throw new ResourceNotFoundException("Location was not found");
        }
    }

    private static Set<OrderStatus> activeStatusFilter(OrderStatus status) {
        if (!status.isActive()) {
            throw new InvalidOrderRequestException("Status filter must select an active order status");
        }
        return Set.of(status);
    }
}
