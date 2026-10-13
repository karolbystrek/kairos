package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Import(OrderServiceIntegrationTests.ClockConfiguration.class)
class OrderServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private OrderService orderService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private MutableClock clock;

    private UUID locationId;
    private StaffPrincipal principal;

    @BeforeEach
    void createTestLocation() {
        clock.setInstant(Instant.parse("2026-07-24T23:59:00Z"));
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
            locationId,
            tenantId,
            "Test location",
            "test location"
        );
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
            """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            "admin-" + accountId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
        );
        principal = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
    }

    @Test
    void createsTransitionsAndTracksAnOrder() {
        var created = orderService.createOrder(principal, locationId, null);

        assertThat(created.status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(created.number()).isEqualTo(1);
        assertThat(created.label()).isNull();
        assertThat(created.trackingReference()).isNotNull();

        var ready = orderService.updateStatus(principal, created.id(), OrderStatus.READY);
        var completed = orderService.updateStatus(principal, created.id(), OrderStatus.COMPLETED);
        var tracked = orderService.findTrackedOrder(created.trackingReference());

        assertThat(ready.status()).isEqualTo(OrderStatus.READY);
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(tracked.number()).isEqualTo(1);
        assertThat(tracked.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_history", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
            "SELECT initiator_type FROM order_history WHERE order_id = ? ORDER BY id",
            String.class,
            created.id()
        )).containsOnly("USER");
        assertThat(jdbcTemplate.queryForList(
            "SELECT initiator_id FROM order_history WHERE order_id = ? ORDER BY id",
            UUID.class,
            created.id()
        )).containsOnly(principal.accountId());
    }

    @Test
    void rejectsInvalidTransitions() {
        var created = orderService.createOrder(principal, locationId, null);

        assertThatThrownBy(() -> orderService.updateStatus(principal, created.id(), OrderStatus.COMPLETED))
            .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void numbersEveryOrderIndependentlyOfItsLabel() {
        var first = orderService.createOrder(principal, locationId, null);
        var custom = orderService.createOrder(principal, locationId, "Table 4");
        orderService.updateStatus(principal, first.id(), OrderStatus.CANCELED);
        var second = orderService.createOrder(principal, locationId, null);
        var duplicate = orderService.createOrder(principal, locationId, "3");

        assertThat(List.of(first.number(), custom.number(), second.number(), duplicate.number()))
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(custom.label()).isEqualTo("Table 4");
        assertThat(duplicate.label()).isEqualTo("3");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE location_id = ?",
                Long.class,
                locationId
        )).isEqualTo(4);
    }

    @Test
    void restartsNumbersAtEachLocationsLocalMidnight() {
        var warsawLocationId = UUID.randomUUID();
        var tenantId = principal.tenantId();
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id, name, normalized_name, time_zone) VALUES (?, ?, ?, ?, ?)",
                warsawLocationId,
                tenantId,
                "Warsaw location",
                "warsaw location",
                "Europe/Warsaw"
        );

        var utcOrder = orderService.createOrder(principal, locationId, null);
        var warsawOrder = orderService.createOrder(principal, warsawLocationId, null);
        clock.setInstant(Instant.parse("2026-07-25T00:01:00Z"));
        var nextUtcDayOrder = orderService.createOrder(principal, locationId, null);
        var sameWarsawDayOrder = orderService.createOrder(principal, warsawLocationId, null);

        assertThat(List.of(utcOrder.number(), warsawOrder.number(), nextUtcDayOrder.number(), sameWarsawDayOrder.number()))
                .containsExactly(1L, 1L, 1L, 2L);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> instant =
                new AtomicReference<>(Instant.parse("2026-07-24T12:00:00Z"));

        void setInstant(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
