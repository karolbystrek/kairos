package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderEventResponse;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;

public class OrderSseEmitterRegistry {
    public static final String STATUS_CHANGED_EVENT_NAME = "order-status-changed";
    // Keyed by tracking reference so an event reaches only its own order's streams.
    private final ConcurrentHashMap<UUID, Map<SseEmitter, Connection>> connections = new ConcurrentHashMap<>();
    private final LongFunction<SseEmitter> emitterFactory;
    private final Executor executor;

    protected OrderSseEmitterRegistry(LongFunction<SseEmitter> emitterFactory) {
        this(emitterFactory, Runnable::run);
    }

    OrderSseEmitterRegistry(LongFunction<SseEmitter> emitterFactory, Executor executor) {
        this.emitterFactory = emitterFactory;
        this.executor = executor;
    }

    private final class Connection {
        private final SseConnection delivery;
        private final CoalescingWorker recovery;
        private volatile OrderService orders;
        private OrderStatus latest;

        private Connection(UUID reference, SseEmitter emitter) {
            delivery = new SseConnection(emitter, () -> remove(reference, emitter), executor);
            recovery = new CoalescingWorker(executor, () -> {
                try {
                    accept(OrderStatusChangedEvent.snapshot(reference, orders.findTrackedOrder(reference)));
                } catch (RuntimeException exception) {
                    delivery.close();
                }
                return false;
            });
        }

        private synchronized void accept(OrderStatusChangedEvent event) {
            if (latest != null && (latest.isTerminal() && latest != event.status()
                    || latest == OrderStatus.READY && event.status() == OrderStatus.IN_PREPARATION)) return;
            latest = event.status();
            delivery.send(SseEmitter.event().name(STATUS_CHANGED_EVENT_NAME)
                    .data(CustomerOrderEventResponse.from(event)), event.status().isTerminal());
        }
    }

    public SseEmitter subscribe(UUID reference, OrderService orders) {
        var emitter = register(reference);
        var connection = connections.getOrDefault(reference, Map.of()).get(emitter);
        try {
            var initial = orders.findTrackedOrder(reference);
            if (connection != null) {
                connection.orders = orders;
                connection.accept(OrderStatusChangedEvent.snapshot(reference, initial));
            }
            return emitter;
        } catch (RuntimeException exception) {
            complete(reference, emitter);
            throw exception;
        }
    }

    public void refresh(OrderService orders) {
        all().forEach(connection -> {
            connection.orders = orders;
            connection.recovery.wake();
        });
    }

    public SseEmitter register(UUID reference) {
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(reference, emitter);
        // compute keeps the add atomic with a concurrent removal of the reference's last stream.
        connections.compute(reference, (ignored, streams) -> {
            var current = streams == null ? new ConcurrentHashMap<SseEmitter, Connection>() : streams;
            current.put(emitter, connection);
            return current;
        });
        connection.delivery.send(SseEmitter.event().comment("connected"), false);
        return emitter;
    }

    public void complete(UUID reference, SseEmitter emitter) {
        var connection = connections.getOrDefault(reference, Map.of()).get(emitter);
        remove(reference, emitter);
        if (connection != null) connection.delivery.close();
    }

    public void forward(OrderStatusChangedEvent event) {
        connections.getOrDefault(event.trackingReference(), Map.of()).values()
                .forEach(connection -> connection.accept(event));
    }

    @Scheduled(fixedRate = 15_000)
    void sendHeartbeats() {
        all().forEach(connection -> connection.delivery.heartbeat());
    }

    int connectionCount(UUID reference) {
        return connections.getOrDefault(reference, Map.of()).size();
    }

    private void remove(UUID reference, SseEmitter emitter) {
        connections.computeIfPresent(reference, (ignored, streams) -> {
            streams.remove(emitter);
            return streams.isEmpty() ? null : streams;
        });
    }

    private Stream<Connection> all() {
        return connections.values().stream().flatMap(streams -> streams.values().stream());
    }
}
