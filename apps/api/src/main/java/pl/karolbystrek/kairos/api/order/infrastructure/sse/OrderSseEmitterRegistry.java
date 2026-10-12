package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderEventResponse;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;

public class OrderSseEmitterRegistry {
    public static final String STATUS_CHANGED_EVENT_NAME = "order-status-changed";
    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
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
        private final UUID reference;
        private final SseConnection delivery;
        private final CoalescingWorker recovery;
        private volatile OrderService orders;
        private OrderStatus latest;

        private Connection(UUID reference, SseEmitter emitter) {
            this.reference = reference;
            delivery = new SseConnection(emitter, () -> connections.remove(emitter), executor);
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
        var connection = connections.get(emitter);
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
        connections.values().forEach(connection -> {
            connection.orders = orders;
            connection.recovery.wake();
        });
    }

    public SseEmitter register(UUID reference) {
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(reference, emitter);
        connections.put(emitter, connection);
        connection.delivery.send(SseEmitter.event().comment("connected"), false);
        return emitter;
    }

    public void complete(UUID reference, SseEmitter emitter) {
        var connection = connections.remove(emitter);
        if (connection != null) connection.delivery.close();
    }

    public void forward(OrderStatusChangedEvent event) {
        connections.values().forEach(connection -> {
            if (connection.reference.equals(event.trackingReference())) connection.accept(event);
        });
    }

    @Scheduled(fixedRate = 15_000)
    void sendHeartbeats() {
        connections.values().forEach(connection -> connection.delivery.heartbeat());
    }

    int connectionCount(UUID reference) {
        return (int) connections.values().stream().filter(connection -> connection.reference.equals(reference)).count();
    }
}
