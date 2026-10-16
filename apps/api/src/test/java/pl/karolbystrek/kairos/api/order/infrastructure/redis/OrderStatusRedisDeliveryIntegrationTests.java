package pl.karolbystrek.kairos.api.order.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker.WebhookWorker;
import pl.karolbystrek.kairos.api.notification.infrastructure.worker.CustomerPushWorker;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisTestContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Unlike the other integration tests this one keeps the real Redis listener container, so a lost
// publish, channel mismatch or payload incompatibility between publisher and subscriber fails here.
@SpringBootTest
@AutoConfigureMockMvc
class OrderStatusRedisDeliveryIntegrationTests {

    @MockitoBean
    private CustomerPushWorker customerPushWorker;

    @MockitoBean
    private WebhookWorker webhookWorker;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redis;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.start();
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @Test
    void statusChangePublishedThroughRedisReachesTheCustomerStream() throws Exception {
        awaitSubscriber();
        var tenant = new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        var order = orderService.createOrder(tenant.administrator(), tenant.firstLocationId(), null, List.of());
        var stream = mockMvc.perform(get("/api/tracked-orders/v1/{reference}/events", order.trackingReference())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
        assertThat(awaitContent(stream, "IN_PREPARATION")).contains("IN_PREPARATION");

        orderService.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);

        assertThat(awaitContent(stream, "\"READY\"")).contains("READY");
    }

    // Redis Pub/Sub drops messages nobody listens to yet, and the listener container subscribes asynchronously.
    private void awaitSubscriber() throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            // The reply is the receiver count; an unsupported-version probe is ignored by the subscriber.
            var receivers = redis.convertAndSend(OrderEventRedisConfiguration.ORDER_STATUS_CHANNEL, "{\"version\":0}");
            if (receivers != null && receivers > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("The order status listener never subscribed to Redis");
    }

    private static String awaitContent(MockHttpServletResponse response, String expected) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!response.getContentAsString().contains(expected) && System.nanoTime() < deadline) Thread.sleep(20);
        return response.getContentAsString();
    }
}
