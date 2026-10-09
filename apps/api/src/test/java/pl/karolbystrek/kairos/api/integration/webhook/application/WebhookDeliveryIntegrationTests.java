package pl.karolbystrek.kairos.api.integration.webhook.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ClaimedWebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpResult;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
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
    private OrderService orderService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private JdbcTemplate runtimeDatabase;

    @Autowired
    private DatabaseAccessContext databaseAccess;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
                Set.of(OrderEventType.ORDER_CREATED)
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
                "DELETE FROM order_outbox_events WHERE tenant_id = ?",
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

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id = ?", String.class, success.id())).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("SELECT response_body FROM webhook_deliveries WHERE id = ?", String.class, success.id())).isEqualTo("ok\uFFFDbody");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id = ?", String.class, failure.id())).isEqualTo("DEAD_LETTERED");
        assertThat(jdbcTemplate.queryForObject("SELECT response_body FROM webhook_deliveries WHERE id = ?", String.class, failure.id())).isEqualTo("bad\uFFFDbody");
        assertThat(jdbcTemplate.queryForObject("SELECT error_detail FROM webhook_deliveries WHERE id = ?", String.class, failure.id())).isEqualTo("bad\uFFFDdetail");
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
        claimed.addAll(claimService.claimAvailable());

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

    @Test
    void expiredLeaseReclaimsDeliveryAndRejectsPreviousToken() {
        createOrders(1);
        fanOutAll();
        var previous = claimService.claimAvailable().getFirst();
        jdbcTemplate.update("UPDATE webhook_deliveries SET claim_until = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), previous.id());
        var current = claimService.claimAvailable().getFirst();
        assertThat(current.id()).isEqualTo(previous.id());
        assertThat(current.claimToken()).isNotEqualTo(previous.claimToken());
        var now = Instant.now();
        var result = new WebhookHttpResult(204, null, false, null, null);
        assertThat(completionService.complete(previous.id(), previous.claimToken(), now, now, result)).isFalse();
        assertThat(completionService.complete(current.id(), current.claimToken(), now, now, result)).isTrue();
    }

    @Test
    void fanoutScopeCannotReadOrCreateDeliveryForAnotherEvent() {
        createOrders(2);
        var events = jdbcTemplate.queryForList("SELECT id FROM order_outbox_events WHERE tenant_id = ? ORDER BY occurred_at, id", UUID.class, tenant.tenantId());
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            assertThat(runtimeDatabase.queryForObject("SELECT public.next_webhook_fanout()", UUID.class)).isEqualTo(events.getFirst());
            assertThat(databaseAccess.worker(WorkerOperation.WEBHOOK_FANOUT, events.getFirst(), null)).isTrue();
            assertThat(runtimeDatabase.queryForObject("SELECT count(*) FROM order_outbox_events WHERE id = ?", Long.class, events.getLast())).isZero();
        });
        var forbiddenId = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            assertThat(runtimeDatabase.queryForObject("SELECT public.next_webhook_fanout()", UUID.class)).isEqualTo(events.getFirst());
            assertThat(databaseAccess.worker(WorkerOperation.WEBHOOK_FANOUT, events.getFirst(), null)).isTrue();
            runtimeDatabase.update("INSERT INTO webhook_deliveries (id, outbox_event_id, subscription_id, destination_url, payload, status, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING', now())",
                    forbiddenId, events.getLast(), subscriptionId, "http://127.0.0.1:9080/events", "{}");
        })).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM webhook_deliveries WHERE id = ?", Long.class, forbiddenId)).isZero();
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
