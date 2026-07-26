package pl.karolbystrek.kairos.api.integration.webhook.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ClaimedWebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliveryStatus;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpResult;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookDeliveryIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private WebhookSubscriptionManagementService subscriptionService;

    @Autowired
    private WebhookOutboxFanoutService fanoutService;

    @Autowired
    private WebhookDeliveryClaimService claimService;

    @Autowired
    private WebhookDeliveryCompletionService completionService;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private IntegrationTestFixture.TenantFixture tenant;
    private UUID subscriptionId;

    @BeforeEach
    void createFixture() {
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var integration = integrationService.create(
                tenant.administrator(),
                "Delivery worker"
        );
        var subscription = subscriptionService.create(
                tenant.administrator(),
                integration.id(),
                "Order created",
                "http://127.0.0.1:9080/events",
                Set.of(tenant.firstLocationId()),
                Set.of(WebhookEventType.ORDER_CREATED)
        );
        subscriptionId = subscription.subscription().id();
        subscriptionService.changeStatus(
                tenant.administrator(),
                subscriptionId,
                WebhookSubscriptionStatus.ENABLED
        );
    }

    @AfterEach
    void removeCommittedFixture() {
        var tenantId = tenant.tenantId();
        jdbcTemplate.update(
                """
                DELETE FROM webhook_delivery_signing_versions
                WHERE delivery_id IN (
                    SELECT delivery.id
                    FROM webhook_deliveries delivery
                    JOIN webhook_subscriptions subscription
                      ON subscription.id = delivery.subscription_id
                    WHERE subscription.tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM webhook_deliveries
                WHERE subscription_id IN (
                    SELECT id FROM webhook_subscriptions WHERE tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                "DELETE FROM webhook_outbox_events WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM order_history
                WHERE order_id IN (
                    SELECT orders.id
                    FROM orders
                    JOIN locations ON locations.id = orders.location_id
                    WHERE locations.tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM orders
                WHERE location_id IN (
                    SELECT id FROM locations WHERE tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM webhook_signing_secret_versions
                WHERE subscription_id IN (
                    SELECT id FROM webhook_subscriptions WHERE tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM webhook_subscription_event_types
                WHERE subscription_id IN (
                    SELECT id FROM webhook_subscriptions WHERE tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM webhook_subscription_location_access
                WHERE tenant_id = ?
                """,
                tenantId
        );
        jdbcTemplate.update(
                "DELETE FROM webhook_subscriptions WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update(
                "DELETE FROM external_integrations WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update(
                "DELETE FROM location_assignments WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update("DELETE FROM accounts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM locations WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void storesOneAttemptSuccessAndTerminalDeadLetterOutcomes() {
        createOrders(2);
        fanOutAll();
        var claimed = claimService.claimAvailable();
        assertThat(claimed).hasSize(2);

        var attemptedAt = Instant.now();
        var success = claimed.getFirst();
        assertThat(completionService.complete(
                success.id(),
                success.claimToken(),
                attemptedAt,
                attemptedAt.plusMillis(10),
                new WebhookHttpResult(204, "ok\0body", false, null, null)
        )).isTrue();

        var failure = claimed.getLast();
        assertThat(completionService.complete(
                failure.id(),
                UUID.randomUUID(),
                attemptedAt,
                attemptedAt.plusMillis(10),
                new WebhookHttpResult(
                        503,
                        "bad\0body",
                        false,
                        "NON_2XX_RESPONSE",
                        "bad\0detail"
                )
        )).isFalse();
        assertThat(completionService.complete(
                failure.id(),
                failure.claimToken(),
                attemptedAt,
                attemptedAt.plusMillis(10),
                new WebhookHttpResult(
                        503,
                        "bad\0body",
                        false,
                        "NON_2XX_RESPONSE",
                        "bad\0detail"
                )
        )).isTrue();

        var succeeded = deliveryRepository.findById(success.id()).orElseThrow();
        var deadLettered = deliveryRepository.findById(failure.id()).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(succeeded.getResponseBody()).isEqualTo("ok\uFFFDbody");
        assertThat(deadLettered.getStatus())
                .isEqualTo(WebhookDeliveryStatus.DEAD_LETTERED);
        assertThat(deadLettered.getResponseBody()).isEqualTo("bad\uFFFDbody");
        assertThat(deadLettered.getErrorDetail()).isEqualTo("bad\uFFFDdetail");
        assertThat(claimService.claimAvailable()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                JOIN locations ON locations.id = orders.location_id
                WHERE locations.tenant_id = ?
                """,
                Long.class,
                tenant.tenantId()
        )).isEqualTo(2);
    }

    @Test
    void concurrentWorkersClaimEveryDeliveryAtMostOnce() throws Exception {
        createOrders(12);
        fanOutAll();
        List<Callable<List<ClaimedWebhookDelivery>>> tasks = List.of(
                claimService::claimAvailable,
                claimService::claimAvailable
        );

        var claimed = new ArrayList<ClaimedWebhookDelivery>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (var future : executor.invokeAll(tasks)) {
                claimed.addAll(future.get());
            }
        }

        assertThat(claimed).hasSize(12);
        assertThat(claimed)
                .extracting(ClaimedWebhookDelivery::id)
                .doesNotHaveDuplicates();
        assertThat(claimService.claimAvailable()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM webhook_deliveries
                WHERE subscription_id = ?
                  AND status = 'PROCESSING'
                """,
                Long.class,
                subscriptionId
        )).isEqualTo(12);
    }

    private void createOrders(int count) {
        for (var index = 0; index < count; index++) {
            orderService.createOrder(
                    tenant.administrator(),
                    tenant.firstLocationId(),
                    null
            );
        }
    }

    private void fanOutAll() {
        while (fanoutService.fanOutAvailable() > 0) {
            // Continue until every current outbox batch has been processed.
        }
    }
}
