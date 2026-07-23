package pl.karolbystrek.kairos.api.order.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderResponse;

import java.util.UUID;

@RestController
@RequestMapping("/tracked-orders")
@RequiredArgsConstructor
class TrackedOrderController {

    private final OrderService orderService;

    @GetMapping("/{trackingReference}")
    CustomerOrderResponse getTrackedOrder(@PathVariable UUID trackingReference) {
        var trackedOrder = orderService.findTrackedOrder(trackingReference);
        return CustomerOrderResponse.from(trackedOrder);
    }
}
