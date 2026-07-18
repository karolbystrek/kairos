package pl.karolbystrek.kairos.api.order.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.api.model.CreateOrderRequest;
import pl.karolbystrek.kairos.api.order.api.model.LocationResponse;
import pl.karolbystrek.kairos.api.order.api.model.StaffOrderResponse;
import pl.karolbystrek.kairos.api.order.api.model.UpdateOrderStatusRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class OrderManagementController {

	private final OrderService orderService;

	OrderManagementController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping("/locations")
	List<LocationResponse> listLocations() {
		return orderService.listLocations().stream()
			.map(location -> new LocationResponse(location.id(), location.name()))
			.toList();
	}

	@GetMapping("/locations/{locationId}/orders")
	List<StaffOrderResponse> listOrders(@PathVariable UUID locationId) {
		return orderService.listOrders(locationId).stream()
			.map(OrderManagementController::toResponse)
			.toList();
	}

	@PostMapping("/locations/{locationId}/orders")
	@ResponseStatus(HttpStatus.CREATED)
	StaffOrderResponse createOrder(
		@PathVariable UUID locationId,
		@Valid @RequestBody CreateOrderRequest request
	) {
		return toResponse(orderService.createOrder(locationId, request.label()));
	}

	@PatchMapping("/orders/{orderId}/status")
	StaffOrderResponse updateOrderStatus(
		@PathVariable UUID orderId,
		@Valid @RequestBody UpdateOrderStatusRequest request
	) {
		return toResponse(orderService.updateStatus(orderId, request.status()));
	}

	private static StaffOrderResponse toResponse(StaffOrderView order) {
		return new StaffOrderResponse(
			order.id(),
			order.locationId(),
			order.trackingReference(),
			order.label(),
			order.status(),
			order.createdAt(),
			order.updatedAt()
		);
	}
}
