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
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Set<Connection> pending = ConcurrentHashMap.newKeySet();
    private final CoalescingWorker refresh;
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
        // One worker sends every snapshot, so they reach each stream in commit order.
        refresh = new CoalescingWorker(executor, () -> {
            var cycle = List.copyOf(pending);
            pending.removeAll(cycle);
            var snapshots = new HashMap<OrderService.QueueScope, List<OrderView>>();
            var authenticated = new HashSet<Session>();
            cycle.forEach(connection -> {
                if (connections.get(connection.delivery.emitter) != connection) return;
                try {
                    connection.push(snapshots, authenticated);
                } catch (RuntimeException exception) {
                    connection.delivery.close();
                }
            });
            return false;
        });
    }

    private record Session(String subject, Instant signedInAt) {
    }

    private final class Connection {
        private final StaffPrincipal principal;
        private final String subject;
        private final Instant signedInAt;
        private final UUID locationId;
        private final SseConnection delivery;

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
        }

        // Re-checks the session cutoff once per session and cycle, so a revoked session cannot keep a stream open.
        private void push(Map<OrderService.QueueScope, List<OrderView>> snapshots, Set<Session> authenticated) {
            var session = new Session(subject, signedInAt);
            if (!authenticated.contains(session)) {
                authentication.authenticate(subject, signedInAt);
                authenticated.add(session);
            }
            var queue = orders.listOrdersForRefresh(principal, locationId, snapshots);
            delivery.send(SseEmitter.event().name("order-queue")
                    .data(queue.stream().map(OrderResponse::from).toList()), false);
        }
    }

    public SseEmitter subscribe(StaffPrincipal principal, String subject, Instant signedInAt, UUID locationId) {
        // Unauthorized requests fail here, synchronously; the refresh worker then sends the first snapshot.
        orders.listOrdersForRefresh(principal, locationId, new HashMap<>());
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(principal, subject, signedInAt, locationId, emitter);
        connections.put(emitter, connection);
        pending.add(connection);
        refresh.wake();
        return emitter;
    }

    public void forward(OrderStatusChangedEvent event) {
        connections.values().forEach(connection -> {
            if (connection.principal.tenantId().equals(event.tenantId())
                    && (connection.locationId == null || connection.locationId.equals(event.locationId()))) {
                pending.add(connection);
            }
        });
        refresh.wake();
    }

    public void refresh() {
        pending.addAll(connections.values());
        refresh.wake();
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        connections.values().forEach(connection -> connection.delivery.heartbeat());
    }
}
