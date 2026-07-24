package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
class OrderStatusRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publish(OrderStatusChangedEvent event) {
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
