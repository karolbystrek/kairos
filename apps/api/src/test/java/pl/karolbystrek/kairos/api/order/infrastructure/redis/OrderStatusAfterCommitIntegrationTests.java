package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(OrderStatusAfterCommitIntegrationTests.TestConfiguration.class)
class OrderStatusAfterCommitIntegrationTests {

    @jakarta.annotation.Resource
    private TransactionalEventSource eventSource;

    @jakarta.annotation.Resource
    private RecordingStringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedisInteractions() {
        redisTemplate.reset();
    }

    @Test
    void publishesOnlyAfterTheTransactionCommits() {
        eventSource.publish(event());

        assertThat(redisTemplate.channel)
                .isEqualTo(OrderEventRedisConfiguration.ORDER_STATUS_CHANNEL);
        assertThat(redisTemplate.payload).isNotBlank();
    }

    @Test
    void doesNotPublishWhenTheTransactionRollsBack() {
        assertThatThrownBy(() -> eventSource.publishAndRollBack(event()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(redisTemplate.channel).isNull();
        assertThat(redisTemplate.payload).isNull();
    }

    private static OrderStatusChangedEvent event() {
        return new OrderStatusChangedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                OrderStatus.READY,
                Instant.parse("2026-07-24T12:00:00Z")
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:order-event-boundary;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        RecordingStringRedisTemplate redisTemplate() {
            return new RecordingStringRedisTemplate();
        }

        @Bean
        OrderStatusRedisPublisher orderStatusRedisPublisher(
                StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper
        ) {
            return new OrderStatusRedisPublisher(redisTemplate, objectMapper);
        }

        @Bean
        TransactionalEventSource transactionalEventSource(ApplicationEventPublisher eventPublisher) {
            return new TransactionalEventSource(eventPublisher);
        }
    }

    static class TransactionalEventSource {

        private final ApplicationEventPublisher eventPublisher;

        TransactionalEventSource(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void publish(OrderStatusChangedEvent event) {
            eventPublisher.publishEvent(event);
        }

        @Transactional
        public void publishAndRollBack(OrderStatusChangedEvent event) {
            eventPublisher.publishEvent(event);
            throw new IllegalStateException("roll back");
        }
    }

    static class RecordingStringRedisTemplate extends StringRedisTemplate {

        private String channel;
        private String payload;

        @Override
        public void afterPropertiesSet() {
            // This test double never opens a Redis connection.
        }

        @Override
        public Long convertAndSend(String destination, Object message) {
            channel = destination;
            payload = message.toString();
            return 1L;
        }

        void reset() {
            channel = null;
            payload = null;
        }
    }
}
