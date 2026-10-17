package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.api.model.CustomerOrderEventResponse;
import pl.karolbystrek.kairos.api.order.application.model.TrackedOrderView;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OrderSseEmitterRegistryTests {

    private final OrderSseEmitterRegistry registry = new OrderSseEmitterRegistry(ignored -> new RecordingEmitter());

    @Test
    void deliversTerminalSnapshotBeforeClosingTheStream() {
        var reference = UUID.randomUUID();
        var orders = mock(OrderService.class);
        when(orders.findTrackedOrder(reference)).thenReturn(new TrackedOrderView(
                42L, "42", OrderStatus.COMPLETED, Instant.parse("2026-07-24T12:00:00Z"), null));

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
        when(orders.findTrackedOrders(Set.of(reference))).thenReturn(Map.of(reference, new TrackedOrderView(
                42L, "42", OrderStatus.READY, Instant.parse("2026-07-24T12:00:00Z"), null)));
        var first = (RecordingEmitter) registry.register(reference);
        var second = (RecordingEmitter) registry.register(reference);

        registry.refresh(orders);

        assertThat(first.events.getFirst().status()).isEqualTo(OrderStatus.READY);
        assertThat(second.events.getFirst().status()).isEqualTo(OrderStatus.READY);
        assertThat(first.completed).isFalse();
        verify(orders, times(1)).findTrackedOrders(Set.of(reference));
    }

    @Test
    void boundsRecoveryReadsAndContinuesAfterAFailedGroup() {
        var orders = mock(OrderService.class);
        for (var index = 0; index < 1001; index++) registry.register(UUID.randomUUID());
        var sizes = new ArrayList<Integer>();
        when(orders.findTrackedOrders(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> references = invocation.getArgument(0);
            sizes.add(references.size());
            if (sizes.size() == 1) throw new IllegalStateException("temporary failure");
            return Map.of();
        });

        registry.refresh(orders);

        assertThat(sizes).containsExactly(500, 500, 1);
    }

    @Test
    void refusesASixthStreamForOneReference() {
        var reference = UUID.randomUUID();
        var emitters = new ArrayList<RecordingEmitter>();
        for (var index = 0; index < 5; index++) {
            emitters.add((RecordingEmitter) registry.register(reference));
        }

        assertThatThrownBy(() -> registry.register(reference))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
        assertThat(emitters).noneMatch(emitter -> emitter.completed);
        assertThat(registry.connectionCount(reference)).isEqualTo(5);
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
                Instant.parse("2026-07-24T12:00:00Z"), 42L, "42", null, UUID.randomUUID(), UUID.randomUUID());
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
                Instant.parse("2026-07-24T12:00:00Z"), 42L, "42", null, UUID.randomUUID(), UUID.randomUUID());
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

    static Stream<Arguments> sendFailures() {
        return Stream.of(
                Arguments.of("heartbeat", 2, (BiConsumer<OrderSseEmitterRegistry, UUID>) (registry, reference) ->
                        registry.sendHeartbeats()),
                Arguments.of("status event", 2, (BiConsumer<OrderSseEmitterRegistry, UUID>) (registry, reference) ->
                        registry.forward(new OrderStatusChangedEvent(UUID.randomUUID(), reference, OrderStatus.READY,
                                Instant.parse("2026-07-24T12:00:00Z"), 42L, "42", null,
                                UUID.randomUUID(), UUID.randomUUID()))),
                Arguments.of("initial send", 1, (BiConsumer<OrderSseEmitterRegistry, UUID>) (registry, reference) -> { })
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sendFailures")
    void removesAConnectionAfterASendFailsWithoutCompletingItAgain(
            String ignored,
            int failingSend,
            BiConsumer<OrderSseEmitterRegistry, UUID> trigger
    ) {
        var emitter = new FailingEmitter(failingSend);
        var failingRegistry = new OrderSseEmitterRegistry(factoryArgument -> emitter);
        var trackingReference = UUID.randomUUID();
        failingRegistry.register(trackingReference);

        trigger.accept(failingRegistry, trackingReference);

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
                        Instant.parse("2026-07-24T12:00:00Z"), 42L, "41", null, UUID.randomUUID(), UUID.randomUUID()));
                concurrent.forward(new OrderStatusChangedEvent(UUID.randomUUID(), other, OrderStatus.READY,
                        Instant.parse("2026-07-24T12:00:00Z"), 42L, "42", null, UUID.randomUUID(), UUID.randomUUID()));
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
