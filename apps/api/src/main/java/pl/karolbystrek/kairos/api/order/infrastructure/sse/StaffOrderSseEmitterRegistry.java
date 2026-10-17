package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.StaffAuthenticationService;
import pl.karolbystrek.kairos.api.order.api.model.OrderResponse;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;

@Component
public class StaffOrderSseEmitterRegistry {
    private final OrderService orders;
    private final StaffAuthenticationService authentication;
    private final LongFunction<SseEmitter> emitterFactory;
    private final Executor executor;
    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();

    @Autowired
    public StaffOrderSseEmitterRegistry(OrderService orders, StaffAuthenticationService authentication) {
        this(orders, authentication, SseEmitter::new, Thread::startVirtualThread);
    }

    StaffOrderSseEmitterRegistry(
            OrderService orders,
            StaffAuthenticationService authentication,
            LongFunction<SseEmitter> emitterFactory
    ) {
        this(orders, authentication, emitterFactory, Runnable::run);
    }

    StaffOrderSseEmitterRegistry(
            OrderService orders,
            StaffAuthenticationService authentication,
            LongFunction<SseEmitter> emitterFactory,
            Executor executor
    ) {
        this.orders = orders;
        this.authentication = authentication;
        this.emitterFactory = emitterFactory;
        this.executor = executor;
    }

    private final class Connection {
        private final StaffPrincipal principal;
        private final String subject;
        private final Instant signedInAt;
        private final UUID locationId;
        private final SseConnection delivery;
        private final CoalescingWorker refresh;

        private Connection(
                StaffPrincipal principal,
                String subject,
                Instant signedInAt,
                UUID locationId,
                SseEmitter emitter
        ) {
            this.principal = principal;
            this.subject = subject;
            this.signedInAt = signedInAt;
            this.locationId = locationId;
            delivery = new SseConnection(emitter, () -> connections.remove(emitter), executor);
            refresh = new CoalescingWorker(executor, () -> {
                try {
                    push();
                } catch (RuntimeException exception) {
                    delivery.close();
                }
                return false;
            });
        }

        // Re-checks the session cutoff too, so a revoked session cannot keep a stream open.
        private synchronized void push() {
            authentication.authenticate(subject, signedInAt);
            var queue = orders.listOrders(principal, locationId, null);
            delivery.send(SseEmitter.event().name("order-queue")
                    .data(queue.stream().map(OrderResponse::from).toList()), false);
        }
    }

    public SseEmitter subscribe(StaffPrincipal principal, String subject, Instant signedInAt, UUID locationId) {
        // Register before the first query to avoid missing a commit; unauthorized requests fail synchronously.
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(principal, subject, signedInAt, locationId, emitter);
        connections.put(emitter, connection);
        try {
            connection.push();
        } catch (RuntimeException exception) {
            connections.remove(emitter);
            throw exception;
        }
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

    public void refresh() {
        connections.values().forEach(connection -> connection.refresh.wake());
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        connections.values().forEach(connection -> connection.delivery.heartbeat());
    }
}
