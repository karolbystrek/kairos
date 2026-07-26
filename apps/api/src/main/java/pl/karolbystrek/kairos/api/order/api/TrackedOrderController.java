package pl.karolbystrek.kairos.api.order.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderResponse;
import pl.karolbystrek.kairos.api.order.infrastructure.sse.OrderSseEmitterRegistry;

import java.util.UUID;

@RestController
@RequestMapping("/tracked-orders/v1")
@RequiredArgsConstructor
class TrackedOrderController {

    private final OrderService orderService;
    private final OrderSseEmitterRegistry emitterRegistry;

    @GetMapping("/{trackingReference}")
    CustomerOrderResponse getTrackedOrder(@PathVariable UUID trackingReference) {
        var trackedOrder = orderService.findTrackedOrder(trackingReference);
        return CustomerOrderResponse.from(trackedOrder);
    }

    @GetMapping(path = "/{trackingReference}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> subscribe(@PathVariable UUID trackingReference) {
        var emitter = emitterRegistry.register(trackingReference);

        try {
            var trackedOrder = orderService.findTrackedOrder(trackingReference);
            if (!trackedOrder.status().isTerminal()) {
                return ResponseEntity.ok(emitter);
            }
        } catch (RuntimeException exception) {
            emitterRegistry.complete(trackingReference, emitter);
            throw exception;
        }

        emitterRegistry.complete(trackingReference, emitter);
        return ResponseEntity.noContent().build();
    }
}
