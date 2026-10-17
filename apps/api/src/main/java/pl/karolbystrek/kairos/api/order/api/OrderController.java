package pl.karolbystrek.kairos.api.order.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.infrastructure.sse.StaffOrderSseEmitterRegistry;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.order.api.model.CreateOrderRequest;
import pl.karolbystrek.kairos.api.order.api.model.OrderResponse;
import pl.karolbystrek.kairos.api.order.api.model.UpdateOrderStatusRequest;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders/v1")
@RequiredArgsConstructor
class OrderController {

    private final OrderService orderService;
    private final StaffOrderSseEmitterRegistry emitterRegistry;

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@AuthenticationPrincipal StaffPrincipal principal,
                         @RequestParam(required = false) UUID locationId,
                         @RequestAttribute(AuthenticationSessionService.IDENTITY) String subject,
                         @RequestAttribute(AuthenticationSessionService.SIGNED_IN_AT) Instant signedInAt) {
        return emitterRegistry.subscribe(principal, subject, signedInAt, locationId);
    }

    @GetMapping
    List<OrderResponse> listOrders(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) OrderStatus status
    ) {
        var orders = orderService.listOrders(principal, locationId, status);
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse createOrder(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        var order = orderService.createOrder(
                principal,
                request.locationId(),
                request.label(),
                request.orderItems()
        );
        return OrderResponse.from(order);
    }

    @PutMapping("/{orderId}/status")
    OrderResponse updateOrderStatus(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        var order = orderService.updateStatus(principal, orderId, request.status());
        return OrderResponse.from(order);
    }
}
