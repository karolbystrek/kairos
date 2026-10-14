package pl.karolbystrek.kairos.api.monitoring.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.List;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "kairos.monitoring.backlog.initial-delay=24h")
class OperationalBacklogIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired JdbcTemplate runtime;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired OrderService orders;

    @Test
    void exposesOnlyCrossTenantAggregatesAndScrapesCachedSnapshots() {
        var owner = PostgresTestDatabase.ownerDatabase();
        var fixture = new IntegrationTestFixture(owner);
        var first = fixture.createTenant();
        var second = fixture.createTenant();
        var before = counts();
        orders.createOrder(first.administrator(), first.firstLocationId(), null, List.of());
        orders.createOrder(second.administrator(), second.firstLocationId(), null, List.of());
        var after = counts();
        for (var worker : new String[]{"analytics", "webhook_fanout", "push_fanout"}) {
            assertThat(after.get(worker)).isEqualTo(before.get(worker) + 2);
        }
        assertThat(after).containsOnlyKeys("analytics", "webhook_fanout", "push_fanout", "webhook_delivery", "push_delivery");
        assertThat(runtime.queryForObject("SELECT count(*) FROM order_measurements", Long.class)).isZero();
        assertThat(runtime.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_proc p, LATERAL aclexplode(p.proacl) acl
                    WHERE p.oid='public.operational_backlog()'::regprocedure
                    AND acl.grantee=0 AND acl.privilege_type='EXECUTE')
                """, Boolean.class)).isFalse();
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new OperationalBacklogMetrics(runtime, transactions, clock, registry);
            assertThat(registry.get("kairos.worker.backlog.pending").tag("worker", "analytics").gauge().value()).isNaN();
            metrics.refresh();
            var gauge = registry.get("kairos.worker.backlog.pending").tag("worker", "analytics").gauge();
            assertThat(gauge.value()).isEqualTo(after.get("analytics").doubleValue());
            orders.createOrder(first.administrator(), first.firstLocationId(), null, List.of());
            assertThat(gauge.value()).isEqualTo(after.get("analytics").doubleValue());
            metrics.refresh();
            assertThat(gauge.value()).isEqualTo(after.get("analytics").doubleValue() + 1);
            assertThat(registry.get("kairos.worker.backlog.snapshot.up").gauge().value()).isEqualTo(1);
            assertThat(registry.get("kairos.worker.backlog.snapshot.timestamp").gauge().value()).isPositive();
        } finally {
            registry.close();
        }
    }

    private Map<String, Long> counts() {
        return runtime.query("SELECT worker,pending_count FROM public.operational_backlog()",
                (row, index) -> Map.entry(row.getString("worker"), row.getLong("pending_count")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
