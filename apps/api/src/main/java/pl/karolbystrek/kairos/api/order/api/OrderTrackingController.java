package pl.karolbystrek.kairos.api.order.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/order-tracking")
class OrderTrackingController {

	private final OrderService orderService;

	OrderTrackingController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping("/{trackingReference}")
	CustomerOrderResponse getTrackedOrder(@PathVariable UUID trackingReference) {
		var order = orderService.findTrackedOrder(trackingReference);
		return new CustomerOrderResponse(order.label(), order.status(), order.updatedAt());
	}
}
