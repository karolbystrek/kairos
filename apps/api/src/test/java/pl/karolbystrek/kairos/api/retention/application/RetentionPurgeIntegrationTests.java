package pl.karolbystrek.kairos.api.retention.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MutableTestClockConfiguration.class)
class RetentionPurgeIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final Instant ARCHIVED_AT = Instant.parse("2026-07-26T12:00:00Z");

    @Autowired
    private RetentionPurgeService purgeService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private JdbcTemplate runtimeJdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();
    private IntegrationTestFixture.TenantFixture tenant;

    @BeforeEach
    void createFixture() {
        clock.setInstant(ARCHIVED_AT);
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
    }

    @Test
    void purgesArchivedAccountAndItsProviderIdentityOnlyAfterTheGracePeriod() {
        var accountId = tenant.manager().accountId();
        var subject = jdbcTemplate.queryForObject(
                "SELECT provider_subject FROM accounts WHERE id = ?", String.class, accountId);
        insertInvitation(tenant.firstLocationId(), accountId, "REVOKED", ARCHIVED_AT.minus(Duration.ofDays(1)), ARCHIVED_AT);
        jdbcTemplate.update("UPDATE accounts SET status = 'ARCHIVED', archived_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(ARCHIVED_AT), Timestamp.from(ARCHIVED_AT), accountId);

        clock.setInstant(ARCHIVED_AT.plus(Duration.ofDays(29)));
        purgeService.purge();
        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", accountId)).isOne();
        Mockito.verify(identityProvider, Mockito.never()).deleteUser(ArgumentMatchers.anyString());

        // A provider outage defers the purge; the account must not lose its row while its identity survives.
        clock.setInstant(ARCHIVED_AT.plus(Duration.ofDays(31)));
        Mockito.doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(identityProvider).deleteUser(subject);
        purgeService.purge();
        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", accountId)).isOne();

        // An identity already missing at the provider counts as deleted.
        Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(identityProvider).deleteUser(subject);
        purgeService.purge();
        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", accountId)).isZero();
        assertThat(count("SELECT count(*) FROM location_assignments WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT count(*) FROM account_invitations WHERE issued_by_account_id = ?", accountId)).isZero();
        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", tenant.administrator().accountId())).isOne();
    }

    @Test
    void deferredAccountBlocksOnlyItsOwnLocationAndNotTheRestOfTheCycle() {
        var otherTenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var stuck = tenant.manager().accountId();
        var other = otherTenant.manager().accountId();
        var expiredInvitation = insertInvitation(tenant.secondLocationId(), tenant.administrator().accountId(), "PENDING",
                ARCHIVED_AT.minus(Duration.ofDays(38)), null);
        for (var account : new UUID[]{stuck, other}) {
            jdbcTemplate.update("UPDATE accounts SET status = 'ARCHIVED', archived_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(ARCHIVED_AT), Timestamp.from(ARCHIVED_AT), account);
        }
        jdbcTemplate.update("UPDATE locations SET status = 'ARCHIVED', archived_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(ARCHIVED_AT), Timestamp.from(ARCHIVED_AT), tenant.firstLocationId());
        var stuckSubject = jdbcTemplate.queryForObject(
                "SELECT provider_subject FROM accounts WHERE id = ?", String.class, stuck);
        Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST))
                .when(identityProvider).deleteUser(stuckSubject);

        clock.setInstant(ARCHIVED_AT.plus(Duration.ofDays(31)));
        purgeService.purge();

        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", stuck)).isOne();
        assertThat(count("SELECT count(*) FROM locations WHERE id = ?", tenant.firstLocationId())).isOne();
        assertThat(count("SELECT count(*) FROM accounts WHERE id = ?", other)).isZero();
        assertThat(count("SELECT count(*) FROM account_invitations WHERE id = ?", expiredInvitation)).isZero();
    }

    @Test
    void purgeFunctionRefusesLiveDataEvenWithForgedWorkerSettings() {
        var order = orderService.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        var location = tenant.firstLocationId().toString();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            runtimeJdbc.queryForMap("""
                    SELECT set_config('kairos.scope', 'LOCATION_PURGE', true), set_config('kairos.identity', ?, true),
                           set_config('kairos.selected', 'LOCATION_PURGE:' || ?, true)
                    """, location, location);
            runtimeJdbc.queryForObject("SELECT public.purge_retained('LOCATION_PURGE', ?::uuid)", Boolean.class, location);
        })).rootCause().hasMessageContaining("Only deleted data can be purged");

        assertThat(count("SELECT count(*) FROM orders WHERE id = ?", order.id())).isOne();
        assertThat(count("SELECT count(*) FROM order_history WHERE order_id = ?", order.id())).isOne();
    }

    @Test
    void purgeFunctionRefusesUnexpiredInvitationsEvenWithForgedWorkerSettings() {
        var invitation = insertInvitation(tenant.firstLocationId(), tenant.administrator().accountId(),
                "PENDING", Instant.now(), null);
        var identity = invitation.toString();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            runtimeJdbc.queryForMap("""
                    SELECT set_config('kairos.scope', 'INVITATION_PURGE', true), set_config('kairos.identity', ?, true),
                           set_config('kairos.selected', 'INVITATION_PURGE:' || ?, true)
                    """, identity, identity);
            runtimeJdbc.queryForObject("SELECT public.purge_retained('INVITATION_PURGE', ?::uuid)", Boolean.class, identity);
        })).rootCause().hasMessageContaining("Only deleted data can be purged");

        assertThat(count("SELECT count(*) FROM account_invitations WHERE id = ?", invitation)).isOne();
    }

    @Test
    void purgesArchivedLocationWithItsOrdersWithoutTouchingOtherLocations() {
        var otherTenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var archivedOrder = orderService.createOrder(tenant.administrator(), tenant.secondLocationId(), null);
        var keptOrder = orderService.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        var otherTenantOrder = orderService.createOrder(otherTenant.administrator(), otherTenant.secondLocationId(), null);
        insertPushDelivery(archivedOrder.id());
        insertInvitation(tenant.secondLocationId(), tenant.administrator().accountId(), "PENDING", ARCHIVED_AT, null);
        jdbcTemplate.update("UPDATE locations SET status = 'ARCHIVED', archived_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(ARCHIVED_AT), Timestamp.from(ARCHIVED_AT), tenant.secondLocationId());

        clock.setInstant(ARCHIVED_AT.plus(Duration.ofDays(29)));
        purgeService.purge();
        assertThat(count("SELECT count(*) FROM orders WHERE id = ?", archivedOrder.id())).isOne();

        clock.setInstant(ARCHIVED_AT.plus(Duration.ofDays(31)));
        purgeService.purge();
        assertThat(count("SELECT count(*) FROM locations WHERE id = ?", tenant.secondLocationId())).isZero();
        assertThat(count("SELECT count(*) FROM orders WHERE id = ?", archivedOrder.id())).isZero();
        assertThat(count("SELECT count(*) FROM order_history WHERE order_id = ?", archivedOrder.id())).isZero();
        assertThat(count("SELECT count(*) FROM order_outbox_events WHERE order_id = ?", archivedOrder.id())).isZero();
        assertThat(count("SELECT count(*) FROM customer_push_enrollments WHERE order_id = ?", archivedOrder.id())).isZero();
        assertThat(count("SELECT count(*) FROM customer_push_deliveries WHERE order_id = ?", archivedOrder.id())).isZero();
        assertThat(count("SELECT count(*) FROM account_invitations WHERE location_id = ?", tenant.secondLocationId())).isZero();
        for (var order : new UUID[]{keptOrder.id(), otherTenantOrder.id()}) {
            assertThat(count("SELECT count(*) FROM orders WHERE id = ?", order)).isOne();
            assertThat(count("SELECT count(*) FROM order_history WHERE order_id = ?", order)).isOne();
        }
        assertThat(count("SELECT count(*) FROM locations WHERE id = ?", otherTenant.secondLocationId())).isOne();
    }

    @Test
    void purgesInvitationsThirtyDaysAfterTheyEnded() {
        var issuer = tenant.administrator().accountId();
        var expired = insertInvitation(tenant.firstLocationId(), issuer, "PENDING",
                ARCHIVED_AT.minus(Duration.ofDays(38)), null);
        var recentlyRevoked = insertInvitation(tenant.firstLocationId(), issuer, "REVOKED",
                ARCHIVED_AT.minus(Duration.ofDays(40)), ARCHIVED_AT.minus(Duration.ofDays(29)));
        var oldRevoked = insertInvitation(tenant.firstLocationId(), issuer, "REVOKED",
                ARCHIVED_AT.minus(Duration.ofDays(40)), ARCHIVED_AT.minus(Duration.ofDays(31)));

        purgeService.purge();

        assertThat(count("SELECT count(*) FROM account_invitations WHERE id = ?", expired)).isZero();
        assertThat(count("SELECT count(*) FROM account_invitations WHERE id = ?", oldRevoked)).isZero();
        assertThat(count("SELECT count(*) FROM account_invitations WHERE id = ?", recentlyRevoked)).isOne();
    }

    private UUID insertInvitation(UUID locationId, UUID issuerId, String state, Instant createdAt, Instant revokedAt) {
        var id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account_invitations (
                    id, tenant_id, location_id, issued_by_account_id, assignment_role, email, token_hash, state,
                    revocation_reason, revoked_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'OPERATOR', 'invited@example.com', ?, ?, ?, ?, ?, ?, ?)
                """,
                id, tenant.tenantId(), locationId, issuerId, id.toString().replace("-", "").repeat(2), state,
                revokedAt == null ? null : "STAFF_REVOKED", revokedAt == null ? null : Timestamp.from(revokedAt),
                Timestamp.from(createdAt.plus(Duration.ofDays(7))), Timestamp.from(createdAt),
                Timestamp.from(revokedAt == null ? createdAt : revokedAt));
        return id;
    }

    private void insertPushDelivery(UUID orderId) {
        var subscriptionId = UUID.randomUUID();
        var now = Timestamp.from(ARCHIVED_AT);
        jdbcTemplate.update("""
                INSERT INTO customer_push_subscriptions (
                    id, endpoint_hash, endpoint_origin, encrypted_endpoint, endpoint_nonce, p256dh_key,
                    encrypted_auth_secret, auth_secret_nonce, vapid_key_fingerprint, created_at, updated_at, last_seen_at
                ) VALUES (?, ?, 'https://push.example.com', '\\x00', ?, ?, '\\x00', ?, 'test', ?, ?, ?)
                """,
                subscriptionId, subscriptionId.toString().replace("-", "").repeat(2),
                new byte[12], new byte[65], new byte[12], now, now, now);
        jdbcTemplate.update("INSERT INTO customer_push_enrollments (id, subscription_id, order_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), subscriptionId, orderId, now);
        jdbcTemplate.update("""
                INSERT INTO customer_push_deliveries (
                    id, outbox_event_id, subscription_id, order_id, status, next_attempt_at, deadline_at, created_at, completed_at
                ) SELECT ?, e.id, ?, e.order_id, 'ACCEPTED', ?, ?, ?, ? FROM order_outbox_events e WHERE e.order_id = ?
                """,
                UUID.randomUUID(), subscriptionId, now, now, now, now, orderId);
    }

    private long count(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }
}
