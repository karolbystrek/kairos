package pl.karolbystrek.kairos.api.integration.webhook.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security.WebhookSignatureService;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
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
    private OrderService orderService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturedDeliverySurvivesArchivalAndPreservesSigningPreparationFailure(boolean corruptSigningMaterial) throws Exception {
        var server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        var requests = new AtomicInteger();
        var receivedSignature = new AtomicReference<String>();
        var receivedPayload = new AtomicReference<String>();
        server.createContext("/failure", exchange -> {
            requests.incrementAndGet();
            receivedSignature.set(exchange.getRequestHeaders().getFirst("Kairos-Signature"));
            receivedPayload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "recipient failed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
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
            jdbcTemplate.update("UPDATE external_integrations SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", integration.id());
            jdbcTemplate.update("UPDATE locations SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", tenant.firstLocationId());
            jdbcTemplate.update("UPDATE webhook_subscriptions SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", issued.subscription().id());
            jdbcTemplate.update("UPDATE webhook_signing_secret_versions SET retired_at = now() WHERE subscription_id = ?", issued.subscription().id());
            var encryptedSecret = jdbcTemplate.queryForObject("SELECT encrypted_secret FROM webhook_signing_secret_versions WHERE subscription_id = ?", byte[].class, issued.subscription().id());
            if (corruptSigningMaterial) {
                jdbcTemplate.update("UPDATE webhook_signing_secret_versions SET encrypted_secret = ? WHERE subscription_id = ?", new byte[]{1}, issued.subscription().id());
            }
            var claimed = claimService.claimAvailable().getFirst();
            if (corruptSigningMaterial) {
                jdbcTemplate.update("UPDATE webhook_signing_secret_versions SET encrypted_secret = ? WHERE subscription_id = ?", encryptedSecret, issued.subscription().id());
            }

            deliveryProcessor.process(claimed);

            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id = ?", String.class, claimed.id())).isEqualTo("DEAD_LETTERED");
            if (corruptSigningMaterial) {
                assertThat(requests.get()).isZero();
                assertThat(jdbcTemplate.queryForObject("SELECT error_type FROM webhook_deliveries WHERE id = ?", String.class, claimed.id())).isEqualTo("SIGNING_ERROR");
            } else {
                assertThat(requests.get()).isEqualTo(1);
                assertThat(receivedPayload.get()).isEqualTo(claimed.payload());
                var timestamp = Long.parseLong(receivedSignature.get().split(",")[0].substring(2));
                var expectedSignature = new WebhookSignatureService().createHeader(
                        Instant.ofEpochSecond(timestamp), claimed.payload(),
                        List.of(issued.signingSecret().getBytes(StandardCharsets.UTF_8)));
                assertThat(receivedSignature.get()).isEqualTo(expectedSignature);
                assertThat(jdbcTemplate.queryForObject("SELECT response_status FROM webhook_deliveries WHERE id = ?", Integer.class, claimed.id())).isEqualTo(503);
                assertThat(jdbcTemplate.queryForObject("SELECT error_type FROM webhook_deliveries WHERE id = ?", String.class, claimed.id())).isEqualTo("NON_2XX_RESPONSE");
                assertThat(jdbcTemplate.queryForObject("SELECT response_body FROM webhook_deliveries WHERE id = ?", String.class, claimed.id())).isEqualTo("recipient failed");
            }
            assertThat(claimService.claimAvailable()).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?",
                    String.class,
                    order.id()
            )).isEqualTo("IN_PREPARATION");
        } finally {
            server.stop(0);
            removeCommittedFixture(tenant.tenantId());
        }
    }

    private void removeCommittedFixture(UUID tenantId) {
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
