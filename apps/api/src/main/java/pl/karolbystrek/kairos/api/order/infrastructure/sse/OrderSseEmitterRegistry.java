package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class OrderSseEmitterRegistry {

    public static final String STATUS_CHANGED_EVENT_NAME = "order-status-changed";

    private static final Long EMITTER_TIMEOUT = Duration.ofMinutes(30).toMillis();
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByTrackingReference =
            new ConcurrentHashMap<>();
    private final LongFunction<SseEmitter> emitterFactory;

    public SseEmitter register(UUID trackingReference) {
        var emitter = emitterFactory.apply(EMITTER_TIMEOUT);
        emittersByTrackingReference.compute(trackingReference, (ignored, emitters) -> {
            var currentEmitters = emitters == null ? ConcurrentHashMap.<SseEmitter>newKeySet() : emitters;
            currentEmitters.add(emitter);
            return currentEmitters;
        });
        emitter.onCompletion(() -> remove(trackingReference, emitter));
        emitter.onTimeout(() -> remove(trackingReference, emitter));
        emitter.onError(ignored -> remove(trackingReference, emitter));
        send(trackingReference, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void complete(UUID trackingReference, SseEmitter emitter) {
        remove(trackingReference, emitter);
        emitter.complete();
    }

    public void forward(OrderStatusChangedEvent event) {
        var emitters = emittersByTrackingReference.get(event.trackingReference());
        if (emitters == null) {
            return;
        }

        for (var emitter : Set.copyOf(emitters)) {
            send(
                    event.trackingReference(),
                    emitter,
                    SseEmitter.event()
                            .name(STATUS_CHANGED_EVENT_NAME)
                            .data(event)
            );
        }

        if (event.status().isTerminal()) {
            completeAll(event.trackingReference());
        }
    }

    @Scheduled(fixedRate = 15_000)
    void sendHeartbeats() {
        emittersByTrackingReference.forEach((trackingReference, emitters) -> {
            for (var emitter : Set.copyOf(emitters)) {
                send(trackingReference, emitter, SseEmitter.event().comment("heartbeat"));
            }
        });
    }

    int connectionCount(UUID trackingReference) {
        var emitters = emittersByTrackingReference.get(trackingReference);
        return emitters == null ? 0 : emitters.size();
    }

    private void completeAll(UUID trackingReference) {
        var emitters = emittersByTrackingReference.remove(trackingReference);
        if (emitters == null) {
            return;
        }
        emitters.forEach(SseEmitter::complete);
    }

    private void send(
            UUID trackingReference,
            SseEmitter emitter,
            SseEmitter.SseEventBuilder event
    ) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            log.debug(
                    "Removing failed SSE connection for tracking reference {}",
                    trackingReference,
                    exception
            );
            remove(trackingReference, emitter);
        }
    }

    private void remove(UUID trackingReference, SseEmitter emitter) {
        emittersByTrackingReference.computeIfPresent(trackingReference, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
