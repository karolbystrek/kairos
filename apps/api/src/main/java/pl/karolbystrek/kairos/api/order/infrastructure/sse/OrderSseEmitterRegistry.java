package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderEventResponse;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;

@Slf4j
public class OrderSseEmitterRegistry {
    public static final String STATUS_CHANGED_EVENT_NAME = "order-status-changed";
    static final int MAXIMUM_STREAMS_PER_REFERENCE = 5;
    private static final int RECOVERY_GROUP_SIZE = 500;
    // Keyed by tracking reference so an event reaches only its own order's streams.
    private final ConcurrentHashMap<UUID, Map<SseEmitter, Connection>> connections = new ConcurrentHashMap<>();
    private final LongFunction<SseEmitter> emitterFactory;
    private final Executor executor;
    private final CoalescingWorker recovery;
    private volatile OrderService orders;

    protected OrderSseEmitterRegistry(LongFunction<SseEmitter> emitterFactory) {
        this(emitterFactory, Runnable::run);
    }

    OrderSseEmitterRegistry(LongFunction<SseEmitter> emitterFactory, Executor executor) {
        this.emitterFactory = emitterFactory;
        this.executor = executor;
        recovery = new CoalescingWorker(executor, () -> {
            recover();
            return false;
        });
    }

    private final class Connection {
        private final SseConnection delivery;
        private OrderStatus latest;

        private Connection(UUID reference, SseEmitter emitter) {
            delivery = new SseConnection(emitter, () -> remove(reference, emitter), executor);
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
                connection.accept(OrderStatusChangedEvent.snapshot(reference, initial));
            }
            return emitter;
        } catch (RuntimeException exception) {
            complete(reference, emitter);
            throw exception;
        }
    }

    public void refresh(OrderService orders) {
        this.orders = orders;
        recovery.wake();
    }

    // Each group gets its own service transaction and exact-reference authority.
    private void recover() {
        var references = List.copyOf(connections.keySet());
        for (var start = 0; start < references.size(); start += RECOVERY_GROUP_SIZE) {
            var group = Set.copyOf(references.subList(start, Math.min(start + RECOVERY_GROUP_SIZE, references.size())));
            Map<UUID, TrackedOrderView> tracked;
            try {
                tracked = orders.findTrackedOrders(group);
            } catch (RuntimeException exception) {
                log.warn("Could not refresh customer stream group; the next cycle retries", exception);
                continue;
            }
            group.forEach(reference -> {
                var streams = List.copyOf(connections.getOrDefault(reference, Map.of()).values());
                var order = tracked.get(reference);
                streams.forEach(connection -> {
                    if (order == null) {
                        connection.delivery.close();
                    } else {
                        connection.accept(OrderStatusChangedEvent.snapshot(reference, order));
                    }
                });
            });
        }
    }

    public SseEmitter register(UUID reference) {
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(reference, emitter);
        var refused = new boolean[1];
        // compute keeps the add atomic with a concurrent removal of the reference's last stream.
        connections.compute(reference, (ignored, streams) -> {
            var current = streams == null ? new ConcurrentHashMap<SseEmitter, Connection>() : streams;
            // Refusing (not evicting) ends with a closed EventSource, so extra tabs back off instead of evicting each other.
            refused[0] = current.size() >= MAXIMUM_STREAMS_PER_REFERENCE;
            if (!refused[0]) current.put(emitter, connection);
            return current;
        });
        if (refused[0]) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many streams for this order");
        }
        // A random retry spreads the reconnects of every stream after an API restart.
        var retry = 3_000 + ThreadLocalRandom.current().nextLong(10_000);
        connection.delivery.send(SseEmitter.event().reconnectTime(retry).comment("connected"), false);
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
