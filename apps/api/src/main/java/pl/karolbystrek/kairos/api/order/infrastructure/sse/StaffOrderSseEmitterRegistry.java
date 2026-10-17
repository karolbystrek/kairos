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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.LongFunction;

/**
 * Staff queue streams, grouped by queue scope: devices watching one location share one query per change.
 * Each connection's session and access are checked when it opens and again on every {@link #revalidate()}.
 */
@Component
public class StaffOrderSseEmitterRegistry {
    // Bounds queue queries across all groups, so a Redis resubscribe cannot drain the connection pool.
    static final int MAXIMUM_CONCURRENT_REFRESHES = 4;

    private final OrderService orders;
    private final StaffAuthenticationService authentication;
    private final LongFunction<SseEmitter> emitterFactory;
    private final Executor executor;
    private final Semaphore refreshes = new Semaphore(MAXIMUM_CONCURRENT_REFRESHES);
    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Scope, Group> groups = new ConcurrentHashMap<>();

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

    /**
     * Connections with the same scope see the same queue. A tenant-wide request resolves to the principal's own
     * default scope, so it is shared only by that account's devices.
     */
    private record Scope(UUID tenantId, UUID locationId, UUID accountId) {
        static Scope of(StaffPrincipal principal, UUID locationId) {
            return new Scope(principal.tenantId(), locationId, locationId == null ? principal.accountId() : null);
        }

        boolean matches(OrderStatusChangedEvent event) {
            return tenantId.equals(event.tenantId()) && (locationId == null || locationId.equals(event.locationId()));
        }
    }

    private final class Connection {
        private final StaffPrincipal principal;
        private final String subject;
        private final Instant signedInAt;
        private final Scope scope;
        private final SseConnection delivery;
        private final CoalescingWorker validation;

        private Connection(StaffPrincipal principal, String subject, Instant signedInAt, Scope scope, SseEmitter emitter) {
            this.principal = principal;
            this.subject = subject;
            this.signedInAt = signedInAt;
            this.scope = scope;
            delivery = new SseConnection(emitter, () -> remove(this, emitter), executor);
            validation = new CoalescingWorker(executor, () -> {
                try {
                    validate();
                } catch (RuntimeException exception) {
                    delivery.close();
                }
                return false;
            });
        }

        // Re-checks the session cutoff too, so a revoked session cannot keep a stream open.
        private void validate() {
            authentication.authenticate(subject, signedInAt);
            orders.requireQueueAccess(principal, scope.locationId());
        }

        private void send(List<OrderView> queue) {
            delivery.send(SseEmitter.event().name("order-queue")
                    .data(queue.stream().map(OrderResponse::from).toList()), false);
        }
    }

    private final class Group {
        private final Scope scope;
        private final Set<Connection> members = ConcurrentHashMap.newKeySet();
        private final CoalescingWorker refresh;

        private Group(Scope scope) {
            this.scope = scope;
            refresh = new CoalescingWorker(executor, () -> {
                refreshOnce();
                return false;
            });
        }

        // Members were authorized for this scope when they opened and on the latest revalidation.
        private void refreshOnce() {
            for (var member : List.copyOf(members)) {
                List<OrderView> queue;
                refreshes.acquireUninterruptibly();
                try {
                    queue = orders.listOrders(member.principal, scope.locationId(), null);
                } catch (RuntimeException exception) {
                    member.delivery.close();
                    continue;
                } finally {
                    refreshes.release();
                }
                members.forEach(connection -> connection.send(queue));
                return;
            }
        }
    }

    public SseEmitter subscribe(StaffPrincipal principal, String subject, Instant signedInAt, UUID locationId) {
        var emitter = emitterFactory.apply(Duration.ofMinutes(30).toMillis());
        var connection = new Connection(principal, subject, signedInAt, Scope.of(principal, locationId), emitter);
        // Register before the first query to avoid missing a commit; unauthorized requests fail synchronously.
        connections.put(emitter, connection);
        groups.compute(connection.scope, (scope, group) -> {
            var current = group == null ? new Group(scope) : group;
            current.members.add(connection);
            return current;
        });
        try {
            authentication.authenticate(subject, signedInAt);
            connection.send(orders.listOrders(principal, locationId, null));
        } catch (RuntimeException exception) {
            remove(connection, emitter);
            throw exception;
        }
        return emitter;
    }

    public void forward(OrderStatusChangedEvent event) {
        groups.values().forEach(group -> {
            if (group.scope.matches(event)) group.refresh.wake();
        });
    }

    /** Re-sends every queue once per scope, for example after Redis resubscribes. */
    public void refresh() {
        groups.values().forEach(group -> group.refresh.wake());
    }

    /** Re-checks every connection's session and access, then resynchronizes the queues. */
    public void revalidate() {
        connections.values().forEach(connection -> connection.validation.wake());
        refresh();
    }

    private void remove(Connection connection, SseEmitter emitter) {
        connections.remove(emitter);
        groups.computeIfPresent(connection.scope, (scope, group) -> {
            group.members.remove(connection);
            return group.members.isEmpty() ? null : group;
        });
    }

    int groupCount() {
        return groups.size();
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        connections.values().forEach(connection -> connection.delivery.heartbeat());
    }
}
