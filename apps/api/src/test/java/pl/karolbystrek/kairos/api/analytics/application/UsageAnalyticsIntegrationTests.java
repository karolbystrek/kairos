package pl.karolbystrek.kairos.api.analytics.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.dao.DataAccessException;
import pl.karolbystrek.kairos.api.analytics.application.UsageAnalyticsService.Action;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Duration;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MutableTestClockConfiguration.class)
class UsageAnalyticsIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired private UsageAnalyticsService usage;
    @Autowired private OrderService orders;
    @Autowired private MutableTestClock clock;
    @Autowired private JdbcTemplate runtime;
    @Autowired private PlatformTransactionManager transactions;
    private final JdbcTemplate owner = PostgresTestDatabase.ownerDatabase();
    private IntegrationTestFixture.TenantFixture tenant;

    @BeforeEach
    void fixture() { tenant = new IntegrationTestFixture(owner).createTenant(); }

    @Test
    void countsConcurrentAcceptedActionsAndRollsBackWholeInvalidBatch() throws Exception {
        var action = new Action("queue_opened", tenant.firstLocationId());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var calls = List.of(1,2,3,4).stream().map(index -> executor.submit(() -> {
                for (var count = 0; count < 10; count++) usage.panel(tenant.administrator(), List.of(action));
            })).toList();
            for (var call : calls) call.get(15, TimeUnit.SECONDS);
        }
        assertThat(total()).isEqualTo(40);
        assertThat(owner.queryForObject("SELECT hour FROM usage_hourly_analytics WHERE tenant_id=?",
                Timestamp.class, tenant.tenantId()).toInstant()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.HOURS));
        assertThatThrownBy(() -> usage.panel(tenant.administrator(), List.of(
                new Action("menu_editor_opened", null), new Action("queue_opened", UUID.randomUUID()))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(total()).isEqualTo(40);
        assertThat(runtime.queryForObject("SELECT count(*) FROM usage_hourly_analytics", Long.class)).isZero();
    }

    @Test
    void rejectsForeignScopeAndPreservesHistoricalLocationButCascadesTenant() {
        var another = new IntegrationTestFixture(owner).createTenant();
        assertThatThrownBy(() -> usage.panel(tenant.administrator(),
                List.of(new Action("queue_opened", another.firstLocationId())))).isInstanceOf(ResponseStatusException.class);
        usage.panel(tenant.administrator(), List.of(new Action("queue_opened", tenant.firstLocationId()),
                new Action("menu_editor_opened", null)));
        owner.update("DELETE FROM location_assignments WHERE tenant_id=?", tenant.tenantId());
        owner.update("DELETE FROM locations WHERE id=?", tenant.firstLocationId());
        assertThat(total()).isEqualTo(2);
        owner.update("DELETE FROM accounts WHERE tenant_id=?", tenant.tenantId());
        owner.update("DELETE FROM locations WHERE tenant_id=?", tenant.tenantId());
        owner.update("DELETE FROM tenants WHERE id=?", tenant.tenantId());
        assertThat(total()).isZero();
        assertThatThrownBy(() -> usage.panel(tenant.administrator(), List.of(new Action("queue_opened", null))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void acceptsOnlyDueEligibleCompletedReviews() {
        owner.update("UPDATE locations SET google_review_url='https://example.com/review',review_configuration_id=? WHERE id=?",
                UUID.randomUUID(), tenant.firstLocationId());
        var order = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null, List.of());
        assertThatThrownBy(() -> usage.review(order.trackingReference(), "review_link_clicked")).isInstanceOf(RuntimeException.class);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.COMPLETED);
        assertThatThrownBy(() -> usage.review(order.trackingReference(), "review_link_clicked")).isInstanceOf(RuntimeException.class);
        clock.advance(Duration.ofDays(1));
        usage.review(order.trackingReference(), "review_link_clicked");
        usage.review(order.trackingReference(), "review_prompt_dismissed");
        assertThat(total()).isEqualTo(2);
        owner.update("UPDATE locations SET review_configuration_id=? WHERE id=?", UUID.randomUUID(), tenant.firstLocationId());
        assertThatThrownBy(() -> usage.review(order.trackingReference(), "review_link_clicked")).isInstanceOf(RuntimeException.class);
        assertThat(total()).isEqualTo(2);
    }

    @Test
    void restrictsWorkspaceEventsToExistingStaffPermissions() {
        usage.panel(tenant.manager(), List.of(new Action("menu_editor_opened", null)));
        assertThatThrownBy(() -> usage.panel(tenant.manager(), List.of(new Action("integration_settings_opened", null))))
                .isInstanceOf(ResponseStatusException.class);
        owner.update("DELETE FROM location_assignments WHERE account_id=?", tenant.manager().accountId());
        owner.update("INSERT INTO location_assignments(account_id,location_id,tenant_id,role,created_at,updated_at) VALUES(?,?,?,'OPERATOR',?,?)",
                tenant.manager().accountId(), tenant.firstLocationId(), tenant.tenantId(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        for (var event : List.of("menu_editor_opened", "integration_settings_opened")) {
            assertThatThrownBy(() -> usage.panel(tenant.manager(), List.of(new Action(event, null))))
                    .isInstanceOf(ResponseStatusException.class);
        }
        usage.panel(tenant.manager(), List.of(new Action("queue_opened", tenant.firstLocationId())));
        assertThat(total()).isEqualTo(2);
    }

    @Test
    void tenantDeletionWaitsForAcceptedBatchCommit() {
        var deleting = new TransactionTemplate(new JdbcTransactionManager(owner.getDataSource()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            usage.panel(tenant.administrator(), List.of(new Action("queue_opened", null)));
            assertThatThrownBy(() -> deleting.executeWithoutResult(deletion -> {
                owner.execute("SET LOCAL lock_timeout='100ms'");
                owner.update("DELETE FROM tenants WHERE id=?", tenant.tenantId());
            })).isInstanceOf(DataAccessException.class).hasMessageContaining("lock timeout");
        });
        assertThat(total()).isOne();
    }

    private long total() {
        return owner.queryForObject("SELECT COALESCE(sum(action_count),0) FROM usage_hourly_analytics WHERE tenant_id=?",
                Long.class, tenant.tenantId());
    }
}
