package pl.karolbystrek.kairos.api.integration.webhook.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliveryStatus;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WebhookDeliveryProcessorIntegrationTests
        extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private WebhookSubscriptionManagementService subscriptionService;

    @Autowired
    private WebhookOutboxFanoutService fanoutService;

    @Autowired
    private WebhookDeliveryClaimService claimService;

    @Autowired
    private WebhookDeliveryProcessor deliveryProcessor;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void actualNonSuccessResponseBecomesOneTerminalDeadLetter() throws Exception {
        var server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        server.createContext("/failure", exchange -> {
            var response = "recipient failed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
            var integration = integrationService.create(
                    tenant.administrator(),
                    "Processor integration"
            );
            var issued = subscriptionService.create(
                    tenant.administrator(),
                    integration.id(),
                    "Processor failures",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/failure",
                    Set.of(tenant.firstLocationId()),
                    Set.of(OrderEventType.ORDER_CREATED)
            );
            subscriptionService.changeStatus(
                    tenant.administrator(),
                    issued.subscription().id(),
                    WebhookSubscriptionStatus.ENABLED
            );
            var order = orderService.createOrder(
                    tenant.administrator(),
                    tenant.firstLocationId(),
                    null
            );
            assertThat(fanoutService.fanOutAvailable()).isEqualTo(1);
            var claimed = claimService.claimAvailable().getFirst();

            deliveryProcessor.process(claimed);

            var delivery = deliveryRepository.findById(claimed.id()).orElseThrow();
            assertThat(delivery.getStatus())
                    .isEqualTo(WebhookDeliveryStatus.DEAD_LETTERED);
            assertThat(delivery.getResponseStatus()).isEqualTo(503);
            assertThat(delivery.getErrorType()).isEqualTo("NON_2XX_RESPONSE");
            assertThat(delivery.getResponseBody()).isEqualTo("recipient failed");
            assertThat(claimService.claimAvailable()).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?",
                    String.class,
                    order.id()
            )).isEqualTo("IN_PREPARATION");
        } finally {
            server.stop(0);
        }
    }
}
