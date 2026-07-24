package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.order.infrastructure.sse.OrderSseEmitterRegistry;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
class OrderStatusRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final OrderSseEmitterRegistry emitterRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            var payload = new String(message.getBody(), StandardCharsets.UTF_8);
            var redisMessage = objectMapper.readValue(payload, OrderStatusRedisMessage.class);
            if (!redisMessage.isSupported()) {
                log.debug("Ignoring order status event with unsupported version {}", redisMessage.version());
                return;
            }
            emitterRegistry.forward(redisMessage.toApplicationEvent());
        } catch (RuntimeException exception) {
            log.warn("Ignoring invalid order status event received from Redis", exception);
        }
    }
}
