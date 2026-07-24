package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
class OrderEventRedisConfiguration {

    /**
     * Best-effort JSON invalidations for customer order status changes.
     * The payload carries its own schema version.
     */
    static final String ORDER_STATUS_CHANNEL = "kairos.order-status-changed";

    @Bean
    RedisMessageListenerContainer orderStatusRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            OrderStatusRedisSubscriber subscriber
    ) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(ORDER_STATUS_CHANNEL));
        return container;
    }
}
