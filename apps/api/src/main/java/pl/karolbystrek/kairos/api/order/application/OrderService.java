package pl.karolbystrek.kairos.api.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.order.application.model.LocationView;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.Location;
import pl.karolbystrek.kairos.api.order.domain.OrderHistory;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

	private final LocationRepository locationRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderHistoryRepository historyRepository;
	private final Clock clock;

	public OrderService(
		LocationRepository locationRepository,
		CustomerOrderRepository orderRepository,
		OrderHistoryRepository historyRepository,
		Clock clock
	) {
		this.locationRepository = locationRepository;
		this.orderRepository = orderRepository;
		this.historyRepository = historyRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<LocationView> listLocations() {
		return locationRepository.findAllByOrderByNameAsc().stream()
			.map(location -> new LocationView(location.getId(), location.getName()))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<StaffOrderView> listOrders(UUID locationId) {
		requireLocation(locationId);
		return orderRepository.findAllByLocationIdOrderByCreatedAtDesc(locationId).stream()
			.map(this::toStaffResponse)
			.toList();
	}

	@Transactional
	public StaffOrderView createOrder(UUID locationId, String label) {
		Location location = requireLocation(locationId);
		Instant now = clock.instant();
		CustomerOrder order = orderRepository.save(CustomerOrder.create(location, label.trim(), now));
		historyRepository.save(OrderHistory.record(order, order.getStatus(), now));
		return toStaffResponse(order);
	}

	@Transactional
	public StaffOrderView updateStatus(UUID orderId, OrderStatus target) {
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ResourceNotFoundException("Order was not found"));
		Instant now = clock.instant();
		order.transitionTo(target, now);
		historyRepository.save(OrderHistory.record(order, target, now));
		return toStaffResponse(order);
	}

	@Transactional(readOnly = true)
	public TrackedOrderView findTrackedOrder(UUID trackingReference) {
		CustomerOrder order = orderRepository.findByTrackingReference(trackingReference)
			.orElseThrow(() -> new ResourceNotFoundException("Tracked order was not found"));
		return new TrackedOrderView(order.getLabel(), order.getStatus(), order.getUpdatedAt());
	}

	private Location requireLocation(UUID locationId) {
		return locationRepository.findById(locationId)
			.orElseThrow(() -> new ResourceNotFoundException("Location was not found"));
	}

	private StaffOrderView toStaffResponse(CustomerOrder order) {
		return new StaffOrderView(
			order.getId(),
			order.getLocation().getId(),
			order.getTrackingReference(),
			order.getLabel(),
			order.getStatus(),
			order.getCreatedAt(),
			order.getUpdatedAt()
		);
	}
}
