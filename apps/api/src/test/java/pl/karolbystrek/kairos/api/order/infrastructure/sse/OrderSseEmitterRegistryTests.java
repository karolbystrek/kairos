package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSseEmitterRegistryTests {

    private final OrderSseEmitterRegistry registry = new OrderSseEmitterRegistry(ignored -> new RecordingEmitter());

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
                Instant.parse("2026-07-24T12:00:00Z")
        );
        registry.forward(event);

        assertThat(first.events).containsExactly(event);
        assertThat(second.events).containsExactly(event);
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
                Instant.parse("2026-07-24T12:00:00Z")
        );
        registry.forward(event);

        assertThat(first.events).containsExactly(event);
        assertThat(second.events).containsExactly(event);
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
                Instant.parse("2026-07-24T12:00:00Z")
        ));

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

        private final List<OrderStatusChangedEvent> events = new ArrayList<>();
        private boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            for (var part : builder.build()) {
                if (part.getData() instanceof OrderStatusChangedEvent event) {
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
