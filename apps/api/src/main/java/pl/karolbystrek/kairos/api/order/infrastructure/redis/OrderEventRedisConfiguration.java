package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.task.VirtualThreadTaskExecutor;

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
        container.setTaskExecutor(new VirtualThreadTaskExecutor("order-redis-"));
        container.setRecoveryInterval(5_000);
        container.addMessageListener(subscriber, new ChannelTopic(ORDER_STATUS_CHANNEL));
        return container;
    }
}
