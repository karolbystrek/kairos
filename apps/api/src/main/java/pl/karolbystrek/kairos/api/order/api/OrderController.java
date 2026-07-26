package pl.karolbystrek.kairos.api.order.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.api.model.CreateOrderRequest;
import pl.karolbystrek.kairos.api.order.api.model.StaffOrderResponse;
import pl.karolbystrek.kairos.api.order.api.model.UpdateOrderStatusRequest;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders/v1")
@RequiredArgsConstructor
class OrderController {

    private final OrderService orderService;

    @GetMapping
    List<StaffOrderResponse> listOrders(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) OrderStatus status
    ) {
        var orders = orderService.listOrders(principal, locationId, status);
        return orders.stream()
                .map(StaffOrderResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StaffOrderResponse createOrder(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        var order = orderService.createOrder(principal, request.locationId(), request.label());
        return StaffOrderResponse.from(order);
    }

    @PutMapping("/{orderId}/status")
    StaffOrderResponse updateOrderStatus(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        var order = orderService.updateStatus(principal, orderId, request.status());
        return StaffOrderResponse.from(order);
    }
}
