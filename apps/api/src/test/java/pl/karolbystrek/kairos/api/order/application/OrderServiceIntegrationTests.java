package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Transactional
@Import(OrderServiceIntegrationTests.ClockConfiguration.class)
class OrderServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
            locationId,
            tenantId,
            "Test location",
            "test location",
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
        assertThat(created.label()).isEqualTo("1");
        assertThat(created.trackingReference()).isNotNull();

        var ready = orderService.updateStatus(principal, created.id(), OrderStatus.READY);
        var completed = orderService.updateStatus(principal, created.id(), OrderStatus.COMPLETED);
        var tracked = orderService.findTrackedOrder(created.trackingReference());

        assertThat(ready.status()).isEqualTo(OrderStatus.READY);
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(tracked.label()).isEqualTo("1");
        assertThat(tracked.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(historyRepository.count()).isEqualTo(3);
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
    void derivesAutomaticLabelsFromEveryOrderCreatedThatUtcDay() {
        var first = orderService.createOrder(principal, locationId, null);
        var custom = orderService.createOrder(principal, locationId, "Table 4");
        orderService.updateStatus(principal, first.id(), OrderStatus.CANCELED);
        var second = orderService.createOrder(principal, locationId, null);
        var duplicate = orderService.createOrder(principal, locationId, "3");

        assertThat(first.label()).isEqualTo("1");
        assertThat(custom.label()).isEqualTo("Table 4");
        assertThat(second.label()).isEqualTo("3");
        assertThat(duplicate.label()).isEqualTo("3");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE location_id = ?",
                Long.class,
                locationId
        )).isEqualTo(4);
    }

    @Test
    void allocatesNumbersIndependentlyPerLocationAndUtcDate() {
        var secondLocationId = UUID.randomUUID();
        var tenantId = principal.tenantId();
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
                secondLocationId,
                tenantId,
                "Second location",
                "second location",
                "second location"
        );

        var firstLocationOrder = orderService.createOrder(principal, locationId, null);
        var secondLocationOrder = orderService.createOrder(principal, secondLocationId, null);
        clock.setInstant(Instant.parse("2026-07-25T00:01:00Z"));
        var nextUtcDateOrder = orderService.createOrder(principal, locationId, null);

        assertThat(firstLocationOrder.label()).isEqualTo("1");
        assertThat(secondLocationOrder.label()).isEqualTo("1");
        assertThat(nextUtcDateOrder.label()).isEqualTo("1");
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
