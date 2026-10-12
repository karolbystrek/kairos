package pl.karolbystrek.kairos.api.analytics.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.dao.DataAccessException;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.OrderMeasurementRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.retention.application.RetentionPurgeService;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MutableTestClockConfiguration.class)
class OrderAnalyticsIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    private static final Instant CREATED = Instant.parse("2026-07-26T12:59:00Z");

    @Autowired private OrderService orders;
    @Autowired private OrderAnalyticsService analytics;
    @Autowired private RetentionPurgeService retention;
    @Autowired private MutableTestClock clock;
    @Autowired private JdbcTemplate runtime;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DatabaseAccessContext access;
    @Autowired private OrderMeasurementRepository measurements;

    private final JdbcTemplate owner = PostgresTestDatabase.ownerDatabase();
    private IntegrationTestFixture.TenantFixture tenant;

    @BeforeEach
    void fixture() {
        clock.setInstant(CREATED);
        tenant = new IntegrationTestFixture(owner).createTenant();
    }

    @Test
    void recordsAcceptedTransitionsOnceAndAttributesDurationsToFinishingHour() {
        var order = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        clock.advance(Duration.ofMinutes(2));
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);
        clock.advance(Duration.ofMinutes(3));
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.COMPLETED);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.COMPLETED);
        var canceled = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        orders.updateStatus(tenant.administrator(), canceled.id(), OrderStatus.CANCELED);

        assertThat(count("order_measurements")).isEqualTo(5);
        drain();
        assertThat(count("order_measurements")).isZero();
        assertSummary("IN_PREPARATION", "2026-07-26T12:00:00Z", 1, 0, 0);
        assertSummary("IN_PREPARATION", "2026-07-26T13:00:00Z", 1, 0, 0);
        assertSummary("READY", "2026-07-26T13:00:00Z", 1, 1, 120_000);
        assertSummary("COMPLETED", "2026-07-26T13:00:00Z", 1, 1, 180_000);
        assertSummary("CANCELED", "2026-07-26T13:00:00Z", 1, 0, 0);
        assertThat(analytics.processAvailable()).isZero();
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics", Long.class)).isEqualTo(5);
        assertThat(count("order_history")).isEqualTo(5);
        assertThat(count("order_outbox_events")).isEqualTo(5);
    }

    @Test
    void countsTransitionButOmitsNegativeDurationAfterClockMovesBackward() {
        var order = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        clock.setInstant(CREATED.plusSeconds(120));
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);
        clock.setInstant(CREATED.plusSeconds(120).minusNanos(500_000));
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.COMPLETED);
        drain();
        assertSummary("COMPLETED", "2026-07-26T13:00:00Z", 1, 0, 0);
    }

    @Test
    void rollsBackMeasurementWithTheOrderTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
            status.setRollbackOnly();
        });
        assertThat(count("orders")).isZero();
        assertThat(count("order_measurements")).isZero();
        assertThat(analytics.processAvailable()).isZero();
    }

    @Test
    void acceptsSubsequentTransitionsOfExistingOrdersWithoutInventingCreation() {
        var orderId = UUID.randomUUID();
        owner.update("""
                INSERT INTO orders (id,location_id,tracking_reference,label,status,created_at,updated_at)
                VALUES (?,?,?,'Existing','READY',?,?)
                """, orderId, tenant.firstLocationId(), UUID.randomUUID(),
                Timestamp.from(CREATED.minusSeconds(300)), Timestamp.from(CREATED.minusSeconds(90)));
        orders.updateStatus(tenant.administrator(), orderId, OrderStatus.COMPLETED);
        drain();
        assertThat(count("order_hourly_analytics")).isOne();
        assertSummary("COMPLETED", "2026-07-26T12:00:00Z", 1, 1, 90_000);
    }

    @Test
    void retriesFailedAggregationWithoutLosingOrDoubleCountingInput() {
        orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        owner.execute("ALTER TABLE order_hourly_analytics ADD CONSTRAINT reject_test_measurements CHECK (transition_count = 0)");
        try {
            assertThatThrownBy(() -> analytics.processAvailable()).isInstanceOf(RuntimeException.class);
            assertThat(count("order_measurements")).isOne();
            assertThat(count("order_hourly_analytics")).isZero();
        } finally {
            owner.execute("ALTER TABLE order_hourly_analytics DROP CONSTRAINT reject_test_measurements");
        }
        drain();
        assertSummary("IN_PREPARATION", "2026-07-26T12:00:00Z", 1, 0, 0);
        assertThat(analytics.processAvailable()).isZero();
    }

    @Test
    void concurrentWorkersDrainBacklogExactlyOnce() throws Exception {
        var another = new IntegrationTestFixture(owner).createTenant();
        for (var fixture : List.of(tenant, another)) {
            owner.update("""
                    INSERT INTO order_measurements(id,tenant_id,location_id,status,occurred_at)
                    SELECT gen_random_uuid(),?,?,'IN_PREPARATION',? FROM generate_series(1,1005)
                    """, fixture.tenantId(), fixture.firstLocationId(), Timestamp.from(CREATED));
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); drain(); return null; });
            var second = executor.submit(() -> { start.await(); drain(); return null; });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        drain();
        assertThat(count("order_measurements")).isZero();
        assertSummary("IN_PREPARATION", "2026-07-26T12:00:00Z", 1005, 0, 0);
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics WHERE tenant_id=?",
                Long.class, another.tenantId())).isEqualTo(1005);
    }

    @Test
    void retainsAggregatesAndPendingMeasurementsAcrossActualLocationPurge() {
        orders.createOrder(tenant.administrator(), tenant.secondLocationId(), null);
        drain();
        orders.createOrder(tenant.administrator(), tenant.secondLocationId(), null);
        owner.update("UPDATE locations SET status='ARCHIVED',archived_at=?,updated_at=? WHERE id=?",
                Timestamp.from(CREATED), Timestamp.from(CREATED), tenant.secondLocationId());
        clock.advance(Duration.ofDays(31));
        retention.purge();
        assertThat(owner.queryForObject("SELECT count(*) FROM locations WHERE id=?", Long.class, tenant.secondLocationId())).isZero();
        assertThat(count("order_measurements")).isOne();
        drain();
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics WHERE location_id=?",
                Long.class, tenant.secondLocationId())).isEqualTo(2);
    }

    @Test
    void runtimeWithoutCapabilityCannotReadOrWriteEitherTenantMeasurements() {
        orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        var another = new IntegrationTestFixture(owner).createTenant();
        orders.createOrder(another.administrator(), another.firstLocationId(), null);
        assertThat(runtime.queryForObject("SELECT count(*) FROM order_measurements", Long.class)).isZero();
        drain();
        assertThat(runtime.queryForObject("SELECT count(*) FROM order_hourly_analytics", Long.class)).isZero();
        assertThatThrownBy(() -> runtime.update("""
                INSERT INTO order_measurements(id,tenant_id,location_id,status,occurred_at)
                VALUES (?,?,?,'IN_PREPARATION',?)
                """, UUID.randomUUID(), tenant.tenantId(), tenant.firstLocationId(), Timestamp.from(CREATED)))
                .isInstanceOf(RuntimeException.class);
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics WHERE tenant_id=?",
                Long.class, tenant.tenantId())).isOne();
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics WHERE tenant_id=?",
                Long.class, another.tenantId())).isOne();
    }

    @Test
    void staffCannotInsertMeasurementForAnotherTenantAndWorkerSeesOnlyDiscoveredInput() {
        var another = new IntegrationTestFixture(owner).createTenant();
        orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        orders.createOrder(another.administrator(), another.firstLocationId(), null);
        drain();
        orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        orders.createOrder(another.administrator(), another.firstLocationId(), null);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.staff(tenant.administrator());
            runtime.update("""
                    INSERT INTO order_measurements(id,tenant_id,location_id,status,occurred_at)
                    VALUES (?,?,?,'IN_PREPARATION',?)
                    """, UUID.randomUUID(), another.tenantId(), another.firstLocationId(), Timestamp.from(CREATED));
        })).isInstanceOf(RuntimeException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var selected = runtime.queryForObject("SELECT public.next_order_measurement()", UUID.class);
            assertThat(selected).isNotNull();
            assertThat(access.worker(WorkerOperation.ANALYTICS, selected, null)).isTrue();
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_measurements", Long.class)).isOne();
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_measurements WHERE id<>?", Long.class, selected)).isZero();
            var selectedTenant = runtime.queryForObject("SELECT tenant_id FROM order_measurements WHERE id=?", UUID.class, selected);
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_hourly_analytics WHERE tenant_id<>?",
                    Long.class, selectedTenant)).isZero();
        });
        drain();
        assertThat(owner.queryForObject("SELECT sum(transition_count) FROM order_hourly_analytics", Long.class)).isEqualTo(4);
    }

    @Test
    void permanentTenantDeletionErasesPendingAndAggregatesAndCannotRecreateThem() {
        var deleted = UUID.randomUUID();
        var location = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES (?)", deleted);
        insertMeasurement(deleted, location);
        drain();
        insertMeasurement(deleted, location);
        owner.update("DELETE FROM tenants WHERE id=?", deleted);
        drain();
        assertThat(owner.queryForObject("SELECT count(*) FROM order_measurements WHERE tenant_id=?", Long.class, deleted)).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM order_hourly_analytics WHERE tenant_id=?", Long.class, deleted)).isZero();
        assertThatThrownBy(() -> insertMeasurement(deleted, location)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void mergesFixedHistogramBucketsWithExactBoundariesAndOverflow() {
        var durations = new long[]{0, 59_999, 60_000, 119_999, 120_000, 299_999, 300_000, 599_999,
                600_000, 899_999, 900_000, 1_799_999, 1_800_000, 3_599_999, 3_600_000, 7_200_000};
        for (var duration : durations) {
            owner.update("""
                    INSERT INTO order_measurements(id,tenant_id,location_id,status,occurred_at,duration_ms)
                    VALUES (?,?,?,'READY',?,?)
                    """, UUID.randomUUID(), tenant.tenantId(), tenant.firstLocationId(), Timestamp.from(CREATED), duration);
        }
        drain();
        var row = owner.queryForMap("SELECT * FROM order_hourly_analytics WHERE tenant_id=?", tenant.tenantId());
        for (var bucket = 0; bucket < 8; bucket++) {
            assertThat(((Number) row.get("bucket_" + bucket)).longValue()).isEqualTo(2);
        }
        assertSummary("READY", "2026-07-26T12:00:00Z", durations.length, durations.length,
                Arrays.stream(durations).sum());
    }

    @Test
    void workerSkipsTenantBeingDeletedAndNeverRecreatesItsAggregate() throws Exception {
        var deleted = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES (?)", deleted);
        insertMeasurement(deleted, UUID.randomUUID());
        try (var deletion = owner.getDataSource().getConnection()) {
            deletion.setAutoCommit(false);
            try (var statement = deletion.prepareStatement("DELETE FROM tenants WHERE id=?")) {
                statement.setObject(1, deleted);
                statement.executeUpdate();
            }
            // Discovery must skip the tenant lock, rather than wait behind deletion holding input locks.
            var executor = Executors.newSingleThreadExecutor();
            try {
                assertThat(executor.submit(analytics::processAvailable).get(5, TimeUnit.SECONDS)).isZero();
            } finally {
                executor.shutdownNow();
            }
            deletion.commit();
        }
        drain();
        assertThat(count("order_measurements")).isZero();
        assertThat(count("order_hourly_analytics")).isZero();
    }

    @Test
    void tenantDeletionWaitsForWorkerThenErasesItsCommittedAggregate() {
        var deleted = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES (?)", deleted);
        insertMeasurement(deleted, UUID.randomUUID());
        var deletion = new TransactionTemplate(new JdbcTransactionManager(owner.getDataSource()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var selected = runtime.queryForObject("SELECT public.next_order_measurement()", UUID.class);
            assertThat(access.worker(WorkerOperation.ANALYTICS, selected, null)).isTrue();
            measurements.aggregateAndConsume(selected);
            assertThatThrownBy(() -> deletion.executeWithoutResult(deletionStatus -> {
                owner.execute("SET LOCAL lock_timeout='100ms'");
                owner.update("DELETE FROM tenants WHERE id=?", deleted);
            })).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("lock timeout");
        });
        assertThat(count("order_hourly_analytics")).isOne();
        owner.update("DELETE FROM tenants WHERE id=?", deleted);
        assertThat(count("order_hourly_analytics")).isZero();
        assertThat(count("order_measurements")).isZero();
        assertThat(analytics.processAvailable()).isZero();
    }

    private void insertMeasurement(UUID tenantId, UUID locationId) {
        owner.update("""
                INSERT INTO order_measurements(id,tenant_id,location_id,status,occurred_at)
                VALUES (?,?,?,'IN_PREPARATION',?)
                """, UUID.randomUUID(), tenantId, locationId, Timestamp.from(CREATED));
    }

    private void drain() {
        for (var attempts = 0; attempts < 100; attempts++) {
            if (analytics.processAvailable() == 0) return;
        }
        throw new AssertionError("Analytics did not finish its bounded backlog");
    }

    private long count(String table) {
        return owner.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private void assertSummary(String status, String hour, long transitions, long durations, long milliseconds) {
        var row = owner.queryForMap("""
                SELECT transition_count,duration_count,total_duration_ms,bucket_version,
                       bucket_0+bucket_1+bucket_2+bucket_3+bucket_4+bucket_5+bucket_6+bucket_7 AS bucket_count
                FROM order_hourly_analytics WHERE tenant_id=? AND location_id=? AND status=? AND hour=?
                """, tenant.tenantId(), tenant.firstLocationId(), status, Timestamp.from(Instant.parse(hour)));
        assertThat(((Number) row.get("transition_count")).longValue()).isEqualTo(transitions);
        assertThat(((Number) row.get("duration_count")).longValue()).isEqualTo(durations);
        assertThat(((Number) row.get("total_duration_ms")).longValue()).isEqualTo(milliseconds);
        assertThat(((Number) row.get("bucket_version")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("bucket_count")).longValue()).isEqualTo(durations);
    }
}
