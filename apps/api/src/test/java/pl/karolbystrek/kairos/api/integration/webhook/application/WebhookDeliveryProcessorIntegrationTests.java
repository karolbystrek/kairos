package pl.karolbystrek.kairos.api.integration.webhook.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
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
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var committed = false;
        try {
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

            TestTransaction.flagForCommit();
            TestTransaction.end();
            committed = true;

            deliveryProcessor.process(claimed);

            // Read through a fresh transaction so the result must be durable.
            TestTransaction.start();

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
            if (committed) {
                if (!TestTransaction.isActive()) TestTransaction.start();
                removeCommittedFixture(tenant.tenantId());
                TestTransaction.flagForCommit();
                TestTransaction.end();
            }
        }
    }

    private void removeCommittedFixture(java.util.UUID tenantId) {
        jdbcTemplate.update("DELETE FROM webhook_delivery_signing_versions WHERE delivery_id IN (SELECT id FROM webhook_deliveries WHERE outbox_event_id IN (SELECT id FROM order_outbox_events WHERE tenant_id = ?))", tenantId);
        jdbcTemplate.update("DELETE FROM webhook_deliveries WHERE outbox_event_id IN (SELECT id FROM order_outbox_events WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM order_outbox_events WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM order_history WHERE order_id IN (SELECT id FROM orders WHERE location_id IN (SELECT id FROM locations WHERE tenant_id = ?))", tenantId);
        jdbcTemplate.update("DELETE FROM orders WHERE location_id IN (SELECT id FROM locations WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM webhook_signing_secret_versions WHERE subscription_id IN (SELECT id FROM webhook_subscriptions WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM webhook_subscription_event_types WHERE subscription_id IN (SELECT id FROM webhook_subscriptions WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM webhook_subscription_location_access WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM webhook_subscriptions WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM external_integrations WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM location_assignments WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM accounts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM locations WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }
}
