package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentOrderCreationIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> tenantIds = new ArrayList<>();

    @AfterEach
    void removeCommittedFixtures() {
        for (var tenantId : tenantIds) {
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
            jdbcTemplate.update("DELETE FROM accounts WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM locations WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        tenantIds.clear();
    }

    @Test
    void allocatesDistinctMonotonicNumbersForConcurrentCreationAtOneLocation() throws Exception {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var principal = insertAdministrator(tenantId);
        var tasks = new ArrayList<Callable<String>>();
        for (var index = 0; index < 6; index++) {
            tasks.add(() -> orderService.createOrder(principal, locationId, null).label());
        }

        List<String> labels;
        try (var executor = Executors.newFixedThreadPool(6)) {
            labels = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .sorted(Comparator.comparingLong(Long::parseLong))
                    .toList();
        }

        assertThat(labels).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void keepsConcurrentCountersIndependentAcrossLocations() throws Exception {
        var tenantId = insertTenant();
        var firstLocationId = insertLocation(tenantId);
        var secondLocationId = insertLocation(tenantId);
        var firstPrincipal = insertAdministrator(tenantId);
        var secondPrincipal = insertAdministrator(tenantId);
        List<Callable<String>> tasks = List.of(
                () -> orderService.createOrder(firstPrincipal, firstLocationId, null).label(),
                () -> orderService.createOrder(secondPrincipal, secondLocationId, null).label()
        );

        List<String> labels;
        try (var executor = Executors.newFixedThreadPool(2)) {
            labels = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
        }

        assertThat(labels).containsExactlyInAnyOrder("1", "1");
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        tenantIds.add(tenantId);
        return tenantId;
    }

    private UUID insertLocation(UUID tenantId) {
        var locationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
                locationId,
                tenantId
        );
        return locationId;
    }

    private StaffPrincipal insertAdministrator(UUID tenantId) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    id, tenant_id, username, tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?)
                """,
                accountId,
                tenantId,
                "concurrent-admin-" + accountId,
                now,
                now
        );
        return new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
    }
}
