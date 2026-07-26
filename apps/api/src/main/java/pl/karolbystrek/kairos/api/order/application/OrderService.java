package pl.karolbystrek.kairos.api.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.application.model.OrderInitiator;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final LocationRepository locationRepository;
    private final CustomerOrderRepository orderRepository;
    private final StaffAccessService staffAccessService;
    private final OrderCommandService commandService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StaffOrderView> listOrders(
            StaffPrincipal principal,
            UUID locationId,
            OrderStatus status
    ) {
        var access = staffAccessService.resolve(principal);
        var statuses = status == null ? OrderStatus.activeStatuses() : activeStatusFilter(status);

        if (locationId != null) {
            requireAccessibleLocation(access, locationId);
            return orderRepository.findAllByLocationIdAndStatusInOrderByCreatedAtDesc(
                            locationId,
                            statuses
                    ).stream()
                    .map(StaffOrderView::from)
                    .toList();
        }
        if (!access.isTenantAdmin()) {
            return orderRepository.findAllByLocationIdAndStatusInOrderByCreatedAtDesc(
                            access.locationId(),
                            statuses
                    ).stream()
                    .map(StaffOrderView::from)
                    .toList();
        }

        return orderRepository.findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(
                        access.tenantId(),
                        statuses
                ).stream()
                .map(StaffOrderView::from)
                .toList();
    }

    @Transactional
    public StaffOrderView createOrder(StaffPrincipal principal, UUID locationId, String customLabel) {
        var access = staffAccessService.resolveForUpdate(principal);
        var location = requireAccessibleLocationForUpdate(access, locationId);
        var now = clock.instant();
        var order = commandService.create(
                location,
                customLabel,
                OrderInitiator.user(access.accountId()),
                null,
                now
        );
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
        commandService.updateStatus(
                order,
                target,
                OrderInitiator.user(access.accountId()),
                now
        );
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

    private static Set<OrderStatus> activeStatusFilter(OrderStatus status) {
        if (!status.isActive()) {
            throw new InvalidOrderRequestException("Status filter must select an active order status");
        }
        return Set.of(status);
    }
}
