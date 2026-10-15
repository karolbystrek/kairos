package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.api.model.OrderResponse;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;

@Component
public class StaffOrderSseEmitterRegistry {
    private final OrderService orders;
    private final LongFunction<SseEmitter> emitterFactory;
    private final Executor executor;
    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();

    @Autowired
    public StaffOrderSseEmitterRegistry(OrderService orders) {
        this(orders, SseEmitter::new, Thread::startVirtualThread);
    }

    StaffOrderSseEmitterRegistry(OrderService orders, LongFunction<SseEmitter> emitterFactory) {
        this(orders, emitterFactory, Runnable::run);
    }

    StaffOrderSseEmitterRegistry(OrderService orders, LongFunction<SseEmitter> emitterFactory, Executor executor) {
        this.orders = orders;
        this.emitterFactory = emitterFactory;
        this.executor = executor;
    }

    private final class Connection {
        private final StaffPrincipal principal;
        private final UUID locationId;
        private final SseConnection delivery;
        private final CoalescingWorker refresh;

        private Connection(StaffPrincipal principal, UUID locationId, SseEmitter emitter) {
            this.principal = principal;
            this.locationId = locationId;
            delivery = new SseConnection(emitter, () -> connections.remove(emitter), executor);
            refresh = new CoalescingWorker(executor, () -> {
                try {
                    var queue = orders.listOrders(principal, locationId, null);
                    delivery.send(SseEmitter.event().name("order-queue")
                            .data(queue.stream().map(OrderResponse::from).toList()), false);
                } catch (RuntimeException exception) {
                    delivery.close();
                }
                return false;
            });
        }
    }

    public SseEmitter subscribe(StaffPrincipal principal, UUID locationId) {
        // Fail unauthorized requests synchronously, then query after registration to avoid missing a commit.
        orders.listOrders(principal, locationId, null);
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(principal, locationId, emitter);
        connections.put(emitter, connection);
        connection.refresh.wake();
        return emitter;
    }

    public void forward(OrderStatusChangedEvent event) {
        connections.values().forEach(connection -> {
            if (connection.principal.tenantId().equals(event.tenantId())
                    && (connection.locationId == null || connection.locationId.equals(event.locationId()))) {
                connection.refresh.wake();
            }
        });
    }

    @Scheduled(fixedDelay = 240_000)
    public void refresh() {
        connections.values().forEach(connection -> connection.refresh.wake());
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        connections.values().forEach(connection -> connection.delivery.heartbeat());
    }
}
