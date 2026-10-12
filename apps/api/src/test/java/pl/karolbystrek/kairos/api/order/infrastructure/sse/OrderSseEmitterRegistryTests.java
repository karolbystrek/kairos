package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderEventResponse;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OrderSseEmitterRegistryTests {

    private final OrderSseEmitterRegistry registry = new OrderSseEmitterRegistry(ignored -> new RecordingEmitter());

    @Test
    void deliversTerminalSnapshotBeforeClosingTheStream() {
        var reference = UUID.randomUUID();
        var orders = mock(OrderService.class);
        when(orders.findTrackedOrder(reference)).thenReturn(new TrackedOrderView(
                "42", OrderStatus.COMPLETED, Instant.parse("2026-07-24T12:00:00Z"), null));

        var emitter = (RecordingEmitter) registry.subscribe(reference, orders);

        assertThat(emitter.events).hasSize(1);
        assertThat(emitter.events.getFirst().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(emitter.events.getFirst().label()).isEqualTo("42");
        assertThat(emitter.completed).isTrue();
        assertThat(registry.connectionCount(reference)).isZero();
    }

    @Test
    void recoveryRefreshesEveryConnectedCustomerFromPostgres() {
        var reference = UUID.randomUUID();
        var orders = mock(OrderService.class);
        when(orders.findTrackedOrder(reference)).thenReturn(new TrackedOrderView(
                "42", OrderStatus.READY, Instant.parse("2026-07-24T12:00:00Z"), null));
        var first = (RecordingEmitter) registry.register(reference);
        var second = (RecordingEmitter) registry.register(reference);

        registry.refresh(orders);

        assertThat(first.events.getFirst().status()).isEqualTo(OrderStatus.READY);
        assertThat(second.events.getFirst().status()).isEqualTo(OrderStatus.READY);
        assertThat(first.completed).isFalse();
    }

    @Test
    void forwardsOnlyToTheMatchingReferenceAndRetainsConnectionsForActiveEvents() {
        var matchingReference = UUID.randomUUID();
        var otherReference = UUID.randomUUID();
        var first = (RecordingEmitter) registry.register(matchingReference);
        var second = (RecordingEmitter) registry.register(matchingReference);
        var other = (RecordingEmitter) registry.register(otherReference);

        var event = new OrderStatusChangedEvent(
                UUID.randomUUID(),
                matchingReference,
                OrderStatus.READY,
                Instant.parse("2026-07-24T12:00:00Z"), "42", null, UUID.randomUUID(), UUID.randomUUID());
        registry.forward(event);

        assertThat(first.events).containsExactly(CustomerOrderEventResponse.from(event));
        assertThat(second.events).containsExactly(CustomerOrderEventResponse.from(event));
        assertThat(other.events).isEmpty();
        assertThat(first.completed).isFalse();
        assertThat(second.completed).isFalse();
        assertThat(other.completed).isFalse();
        assertThat(registry.connectionCount(matchingReference)).isEqualTo(2);
        assertThat(registry.connectionCount(otherReference)).isEqualTo(1);
    }

    @Test
    void completesAndRemovesEveryMatchingConnectionForATerminalEvent() {
        var matchingReference = UUID.randomUUID();
        var otherReference = UUID.randomUUID();
        var first = (RecordingEmitter) registry.register(matchingReference);
        var second = (RecordingEmitter) registry.register(matchingReference);
        var other = (RecordingEmitter) registry.register(otherReference);

        var event = new OrderStatusChangedEvent(
                UUID.randomUUID(),
                matchingReference,
                OrderStatus.CANCELED,
                Instant.parse("2026-07-24T12:00:00Z"), "42", null, UUID.randomUUID(), UUID.randomUUID());
        registry.forward(event);

        assertThat(first.events).containsExactly(CustomerOrderEventResponse.from(event));
        assertThat(second.events).containsExactly(CustomerOrderEventResponse.from(event));
        assertThat(other.events).isEmpty();
        assertThat(first.completed).isTrue();
        assertThat(second.completed).isTrue();
        assertThat(other.completed).isFalse();
        assertThat(registry.connectionCount(matchingReference)).isZero();
        assertThat(registry.connectionCount(otherReference)).isEqualTo(1);
    }

    @Test
    void removesAConnectionAfterAHeartbeatFailureWithoutCompletingItAgain() {
        var emitter = new FailingEmitter(2);
        var failingRegistry = new OrderSseEmitterRegistry(ignored -> emitter);
        var trackingReference = UUID.randomUUID();
        failingRegistry.register(trackingReference);

        failingRegistry.sendHeartbeats();

        assertThat(failingRegistry.connectionCount(trackingReference)).isZero();
        assertThat(emitter.completeWithErrorCalled).isFalse();
    }

    @Test
    void removesAConnectionAfterAStatusEventSendFailsWithoutCompletingItAgain() {
        var emitter = new FailingEmitter(2);
        var failingRegistry = new OrderSseEmitterRegistry(ignored -> emitter);
        var trackingReference = UUID.randomUUID();

        failingRegistry.register(trackingReference);
        failingRegistry.forward(new OrderStatusChangedEvent(
                UUID.randomUUID(),
                trackingReference,
                OrderStatus.READY,
                Instant.parse("2026-07-24T12:00:00Z"), "42", null, UUID.randomUUID(), UUID.randomUUID()));

        assertThat(failingRegistry.connectionCount(trackingReference)).isZero();
        assertThat(emitter.completeWithErrorCalled).isFalse();
    }

    @Test
    void removesAConnectionAfterItsInitialSendFailsWithoutCompletingItAgain() {
        var emitter = new FailingEmitter(1);
        var failingRegistry = new OrderSseEmitterRegistry(ignored -> emitter);
        var trackingReference = UUID.randomUUID();

        failingRegistry.register(trackingReference);

        assertThat(failingRegistry.connectionCount(trackingReference)).isZero();
        assertThat(emitter.completeWithErrorCalled).isFalse();
    }

    @Test
    void aBlockedClientDoesNotDelayOtherConnections() {
        var release = new CountDownLatch(1);
        var delivered = new CountDownLatch(1);
        var emitters = new ArrayDeque<SseEmitter>(List.of(new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    throw new IOException(exception);
                }
            }
        }, new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) {
                if (builder.build().stream().anyMatch(part -> part.getData() instanceof CustomerOrderEventResponse)) {
                    delivered.countDown();
                }
            }
        }));
        var concurrent = new OrderSseEmitterRegistry(ignored -> emitters.removeFirst(), Thread::startVirtualThread);
        var blocked = UUID.randomUUID();
        var other = UUID.randomUUID();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                concurrent.register(blocked);
                concurrent.register(other);
                concurrent.forward(new OrderStatusChangedEvent(UUID.randomUUID(), blocked, OrderStatus.READY,
                        Instant.parse("2026-07-24T12:00:00Z"), "41", null, UUID.randomUUID(), UUID.randomUUID()));
                concurrent.forward(new OrderStatusChangedEvent(UUID.randomUUID(), other, OrderStatus.READY,
                        Instant.parse("2026-07-24T12:00:00Z"), "42", null, UUID.randomUUID(), UUID.randomUUID()));
                assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            });
        } finally {
            release.countDown();
        }
    }

    @Test
    void removesAConnectionWhenTheRequestCompletes() {
        var emitter = new CallbackEmitter();
        var callbackRegistry = new OrderSseEmitterRegistry(ignored -> emitter);
        var trackingReference = UUID.randomUUID();
        callbackRegistry.register(trackingReference);

        emitter.completeRequest();

        assertThat(callbackRegistry.connectionCount(trackingReference)).isZero();
    }

    @Test
    void removesAConnectionWhenTheRequestTimesOut() {
        var emitter = new CallbackEmitter();
        var callbackRegistry = new OrderSseEmitterRegistry(ignored -> emitter);
        var trackingReference = UUID.randomUUID();
        callbackRegistry.register(trackingReference);

        emitter.timeoutRequest();

        assertThat(callbackRegistry.connectionCount(trackingReference)).isZero();
    }

    private static final class RecordingEmitter extends SseEmitter {

        private final List<CustomerOrderEventResponse> events = new ArrayList<>();
        private boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            for (var part : builder.build()) {
                if (part.getData() instanceof CustomerOrderEventResponse event) {
                    events.add(event);
                }
            }
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    private static final class FailingEmitter extends SseEmitter {

        private final int failingSend;
        private int sends;
        private boolean completeWithErrorCalled;

        private FailingEmitter(int failingSend) {
            this.failingSend = failingSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sends++;
            if (sends == failingSend) {
                throw new IOException("Client disconnected");
            }
        }

        @Override
        public void completeWithError(Throwable exception) {
            completeWithErrorCalled = true;
        }
    }

    private static final class CallbackEmitter extends SseEmitter {

        private Runnable completionCallback;
        private Runnable timeoutCallback;

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
        }

        void completeRequest() {
            completionCallback.run();
        }

        void timeoutRequest() {
            timeoutCallback.run();
        }
    }
}
