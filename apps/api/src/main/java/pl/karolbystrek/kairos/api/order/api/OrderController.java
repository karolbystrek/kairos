package pl.karolbystrek.kairos.api.order.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.api.model.CreateOrderRequest;
import pl.karolbystrek.kairos.api.order.api.model.StaffOrderResponse;
import pl.karolbystrek.kairos.api.order.api.model.UpdateOrderStatusRequest;
import pl.karolbystrek.kairos.api.order.application.OrderService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class OrderController {

    private final OrderService orderService;

    @GetMapping("/locations/{locationId}/orders")
    List<StaffOrderResponse> listOrders(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID locationId
    ) {
        var orders = orderService.listOrders(principal, locationId);
        return orders.stream()
                .map(StaffOrderResponse::from)
                .toList();
    }

    @GetMapping("/orders")
    List<StaffOrderResponse> listTenantOrders(@AuthenticationPrincipal StaffPrincipal principal) {
        var orders = orderService.listTenantOrders(principal);
        return orders.stream()
                .map(StaffOrderResponse::from)
                .toList();
    }

    @PostMapping("/locations/{locationId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    StaffOrderResponse createOrder(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID locationId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        var order = orderService.createOrder(principal, locationId, request.label());
        return StaffOrderResponse.from(order);
    }

    @PatchMapping("/orders/{orderId}/status")
    StaffOrderResponse updateOrderStatus(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        var order = orderService.updateStatus(principal, orderId, request.status());
        return StaffOrderResponse.from(order);
    }
}
