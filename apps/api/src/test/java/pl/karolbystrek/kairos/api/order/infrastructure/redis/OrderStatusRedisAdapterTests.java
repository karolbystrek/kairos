package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.sse.OrderSseEmitterRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OrderStatusRedisAdapterTests {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void publishesVersionedJsonAndIsolatesRedisFailures() {
        var redisTemplate = new RecordingStringRedisTemplate();
        var publisher = new OrderStatusRedisPublisher(redisTemplate, objectMapper);
        var event = event(OrderStatus.READY);

        publisher.publish(event);

        assertThat(redisTemplate.channel)
                .isEqualTo(OrderEventRedisConfiguration.ORDER_STATUS_CHANNEL);
        assertThat(redisTemplate.payload)
                .isEqualTo(objectMapper.writeValueAsString(OrderStatusRedisMessage.from(event)));

        redisTemplate.fail = true;
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    void ignoresMalformedAndUnsupportedMessages() {
        var registry = new CapturingEmitterRegistry();
        var subscriber = new OrderStatusRedisSubscriber(objectMapper, registry);

        subscriber.onMessage(message("not-json"), null);
        subscriber.onMessage(message("""
                {
                  "version": 999,
                  "trackingReference": "00000000-0000-0000-0000-000000000001",
                  "status": "READY",
                  "updatedAt": "2026-07-24T12:00:00Z"
                }
                """), null);

        assertThat(registry.events).isEmpty();
    }

    @Test
    void forwardsAValidMessageToTheLocalRegistry() {
        var registry = new CapturingEmitterRegistry();
        var subscriber = new OrderStatusRedisSubscriber(objectMapper, registry);
        var event = event(OrderStatus.COMPLETED);
        var payload = objectMapper.writeValueAsString(OrderStatusRedisMessage.from(event));

        subscriber.onMessage(message(payload), null);

        assertThat(registry.events).containsExactly(event);
    }

    private static DefaultMessage message(String payload) {
        return new DefaultMessage(
                OrderEventRedisConfiguration.ORDER_STATUS_CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static OrderStatusChangedEvent event(OrderStatus status) {
        return new OrderStatusChangedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                status,
                Instant.parse("2026-07-24T12:00:00Z")
        );
    }

    private static class RecordingStringRedisTemplate extends StringRedisTemplate {

        private String channel;
        private String payload;
        private boolean fail;

        @Override
        public Long convertAndSend(String destination, Object message) {
            if (fail) {
                throw new IllegalStateException("Redis unavailable");
            }
            channel = destination;
            payload = message.toString();
            return 1L;
        }
    }

    private static class CapturingEmitterRegistry extends OrderSseEmitterRegistry {

        private final ArrayList<OrderStatusChangedEvent> events = new ArrayList<>();

        private CapturingEmitterRegistry() {
            super(SseEmitter::new);
        }

        @Override
        public void forward(OrderStatusChangedEvent event) {
            events.add(event);
        }
    }
}
