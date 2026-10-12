package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class OrderEventRedisConfiguration {

    /**
     * Best-effort committed order state for customer and staff streams.
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
