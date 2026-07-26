package pl.karolbystrek.kairos.api.order.api.external;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.order.api.external.model.ExternalCreateOrderRequest;
import pl.karolbystrek.kairos.api.order.api.external.model.ExternalOrderPageResponse;
import pl.karolbystrek.kairos.api.order.api.external.model.ExternalOrderResponse;
import pl.karolbystrek.kairos.api.order.api.external.model.ExternalUpdateOrderStatusRequest;
import pl.karolbystrek.kairos.api.order.application.ExternalOrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.util.UUID;

@RestController
@RequestMapping("/external/orders/v1")
@RequiredArgsConstructor
public class ExternalOrderController {

    private final ExternalOrderService orderService;

    @GetMapping
    ExternalOrderPageResponse list(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        var orders = orderService.list(principal, locationId, status, cursor, limit);
        return ExternalOrderPageResponse.from(orders);
    }

    @GetMapping("/{orderId}")
    ExternalOrderResponse get(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @PathVariable UUID orderId
    ) {
        var order = orderService.find(principal, orderId);
        return ExternalOrderResponse.from(order);
    }

    @PostMapping
    ResponseEntity<ExternalOrderResponse> create(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExternalCreateOrderRequest request
    ) {
        var result = orderService.create(
                principal,
                request.locationId(),
                request.label(),
                idempotencyKey
        );
        var status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(ExternalOrderResponse.from(result.order()));
    }

    @PutMapping("/{orderId}/status")
    ExternalOrderResponse updateStatus(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody ExternalUpdateOrderStatusRequest request
    ) {
        var order = orderService.updateStatus(principal, orderId, request.status());
        return ExternalOrderResponse.from(order);
    }
}
