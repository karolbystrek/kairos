package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

@Component
@Slf4j
class OrderStatusRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    @Autowired
    OrderStatusRedisPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, Thread::startVirtualThread);
    }

    OrderStatusRedisPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Executor executor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    // AFTER_COMMIT still holds the JDBC connection, so a stalled Redis must not be awaited on this thread.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publish(OrderStatusChangedEvent event) {
        try {
            executor.execute(() -> send(event));
        } catch (RuntimeException exception) {
            log.warn("Could not schedule order status event for tracking reference {}", event.trackingReference(), exception);
        }
    }

    private void send(OrderStatusChangedEvent event) {
        try {
            var payload = objectMapper.writeValueAsString(OrderStatusRedisMessage.from(event));
            redisTemplate.convertAndSend(OrderEventRedisConfiguration.ORDER_STATUS_CHANNEL, payload);
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not publish committed order status event for tracking reference {}",
                    event.trackingReference(),
                    exception
            );
        }
    }
}
