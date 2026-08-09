package pl.karolbystrek.kairos.api.location.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.location.application.exception.LocationConflictException;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LocationServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsRenamesSortsAndReusesAnArchivedNormalizedName() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN, AccountStatus.ENABLED);

        var kitchen = locationService.create(administrator, "  Kitchen  ");
        var alpha = locationService.create(administrator, "Alpha");

        assertThat(kitchen.name()).isEqualTo("Kitchen");
        assertThat(kitchen.status()).isEqualTo(LocationStatus.ENABLED);
        assertThatThrownBy(() -> locationService.create(administrator, "kItChEn"))
            .isInstanceOf(LocationConflictException.class);

        var renamed = locationService.rename(administrator, kitchen.id(), "  Counter  ");
        assertThat(renamed.name()).isEqualTo("Counter");
        assertThat(locationService.listAccessible(administrator))
            .extracting(location -> location.name())
            .containsExactly("Alpha", "Counter");

        locationService.updateStatus(administrator, kitchen.id(), LocationStatus.DISABLED);
        locationService.delete(administrator, kitchen.id());
        var reused = locationService.create(administrator, "counter");

        assertThat(reused.id()).isNotEqualTo(kitchen.id());
        assertThat(locationService.listAccessible(administrator))
            .extracting(location -> location.name())
            .containsExactly("Alpha", "counter");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM locations WHERE id = ?",
            String.class,
            kitchen.id()
        )).isEqualTo("ARCHIVED");
        assertThat(alpha.id()).isNotEqualTo(reused.id());
    }

    @Test
    void rejectsDisableWithoutAnyCascadeWhileAnActiveOrderExists() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN, AccountStatus.ENABLED);
        var location = locationService.create(administrator, "Active queue");
        var manager = insertAccount(tenantId, TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(manager.accountId(), tenantId, location.id(), AssignmentRole.MANAGER);
        var sessionId = insertSession(manager.accountId());
        var invitationId = insertInvitation(
            tenantId,
            location.id(),
            manager.accountId()
        );
        insertOrder(location.id(), "IN_PREPARATION");

        assertThatThrownBy(() -> locationService.updateStatus(
            administrator,
            location.id(),
            LocationStatus.DISABLED
        ))
            .isInstanceOf(LocationConflictException.class)
            .extracting(exception -> ((LocationConflictException) exception).getReason())
            .isEqualTo(LocationConflictException.Reason.ACTIVE_ORDERS);

        assertThat(value("locations", "status", location.id())).isEqualTo("ENABLED");
        assertThat(value("accounts", "status", manager.accountId())).isEqualTo("ENABLED");
        assertThat(value("account_invitations", "state", invitationId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NULL FROM sessions WHERE id = ?",
            Boolean.class,
            sessionId
        )).isTrue();
    }

    @Test
    void disableEnableAndDeleteApplyTheCompleteAtomicCascade() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN, AccountStatus.ENABLED);
        var target = locationService.create(administrator, "Target");
        var retained = locationService.create(administrator, "Retained");
        var manager = insertAccount(tenantId, TenantRole.MEMBER, AccountStatus.ENABLED);
        var operator = insertAccount(tenantId, TenantRole.MEMBER, AccountStatus.DISABLED);
        insertAssignment(manager.accountId(), tenantId, target.id(), AssignmentRole.MANAGER);
        insertAssignment(operator.accountId(), tenantId, target.id(), AssignmentRole.OPERATOR);
        var managerSession = insertSession(manager.accountId());
        var invitationId = insertInvitation(tenantId, target.id(), manager.accountId());
        var terminalOrderId = insertOrder(target.id(), "COMPLETED");
        var integration = insertIntegrationResources(tenantId, target.id(), retained.id());

        locationService.updateStatus(administrator, target.id(), LocationStatus.DISABLED);
        assertThat(value("accounts", "status", manager.accountId())).isEqualTo("DISABLED");
        assertThat(value("accounts", "status", operator.accountId())).isEqualTo("DISABLED");
        assertThat(value("account_invitations", "state", invitationId)).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
            Boolean.class,
            managerSession
        )).isTrue();

        locationService.updateStatus(administrator, target.id(), LocationStatus.ENABLED);
        assertThat(value("accounts", "status", manager.accountId())).isEqualTo("ENABLED");
        assertThat(value("accounts", "status", operator.accountId())).isEqualTo("ENABLED");

        locationService.updateStatus(administrator, target.id(), LocationStatus.DISABLED);
        locationService.delete(administrator, target.id());

        assertThat(value("locations", "status", target.id())).isEqualTo("ARCHIVED");
        assertThat(value("accounts", "status", manager.accountId())).isEqualTo("ARCHIVED");
        assertThat(value("accounts", "status", operator.accountId())).isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT password_hash IS NULL FROM accounts WHERE id = ?",
            Boolean.class,
            manager.accountId()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM location_assignments WHERE account_id IN (?, ?)",
            Integer.class,
            manager.accountId(),
            operator.accountId()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE id = ? AND location_id = ?",
            Integer.class,
            terminalOrderId,
            target.id()
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_key_location_access WHERE location_id = ?",
            Integer.class,
            target.id()
        )).isZero();
        assertThat(value("api_keys", "revoked_at", integration.singleLocationKeyId()))
            .isNotNull();
        assertThat(value("api_keys", "revoked_at", integration.multiLocationKeyId()))
            .isNull();
        assertThat(value("webhook_subscriptions", "status", integration.singleLocationSubscriptionId()))
            .isEqualTo("ARCHIVED");
        assertThat(value("webhook_subscriptions", "status", integration.multiLocationSubscriptionId()))
            .isEqualTo("ENABLED");
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        return tenantId;
    }

    private StaffPrincipal insertAccount(
        UUID tenantId,
        TenantRole tenantRole,
        AccountStatus status
    ) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id, tenant_id, username, email, password_hash,
                tenant_role, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            tenantId,
            "location-test-" + accountId,
            "location-test-" + accountId + "@example.com",
            "fixture-password-hash",
            tenantRole.name(),
            status.name(),
            now,
            now
        );
        return new StaffPrincipal(accountId, tenantId, tenantRole);
    }

    private void insertAssignment(
        UUID accountId,
        UUID tenantId,
        UUID locationId,
        AssignmentRole role
    ) {
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO location_assignments (
                account_id, location_id, tenant_id, role, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            accountId,
            locationId,
            tenantId,
            role.name(),
            now,
            now
        );
    }

    private UUID insertSession(UUID accountId) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO sessions (
                id, account_id, refresh_token_hash, token_family_id, created_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            accountId,
            "hash-" + id,
            id,
            now,
            now.plus(1, ChronoUnit.DAYS)
        );
        return id;
    }

    private UUID insertInvitation(UUID tenantId, UUID locationId, UUID issuerId) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO account_invitations (
                id, tenant_id, location_id, issued_by_account_id, assignment_role,
                token_hash, state, expires_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'OPERATOR', ?, 'PENDING', ?, ?, ?)
            """,
            id,
            tenantId,
            locationId,
            issuerId,
            "a".repeat(64),
            now.plus(7, ChronoUnit.DAYS),
            now,
            now
        );
        return id;
    }

    private UUID insertOrder(UUID locationId, String status) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO orders (
                id, location_id, tracking_reference, label, status, created_at, updated_at
            ) VALUES (?, ?, ?, '1', ?, ?, ?)
            """,
            id,
            locationId,
            UUID.randomUUID(),
            status,
            now,
            now
        );
        return id;
    }

    private IntegrationResources insertIntegrationResources(
        UUID tenantId,
        UUID targetLocationId,
        UUID retainedLocationId
    ) {
        var now = Instant.now();
        var integrationId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO external_integrations (
                id, tenant_id, name, normalized_name, status,
                created_at, updated_at, last_enabled_at
            ) VALUES (?, ?, 'Test', 'test', 'ENABLED', ?, ?, ?)
            """,
            integrationId,
            tenantId,
            now,
            now,
            now
        );
        var singleKeyId = insertApiKey(
            integrationId,
            tenantId,
            "Single",
            "single",
            targetLocationId,
            null
        );
        var multiKeyId = insertApiKey(
            integrationId,
            tenantId,
            "Multi",
            "multi",
            targetLocationId,
            retainedLocationId
        );
        var singleSubscriptionId = insertSubscription(
            integrationId,
            tenantId,
            "Single",
            "single",
            targetLocationId,
            null
        );
        var multiSubscriptionId = insertSubscription(
            integrationId,
            tenantId,
            "Multi",
            "multi",
            targetLocationId,
            retainedLocationId
        );
        return new IntegrationResources(
            singleKeyId,
            multiKeyId,
            singleSubscriptionId,
            multiSubscriptionId
        );
    }

    private UUID insertApiKey(
        UUID integrationId,
        UUID tenantId,
        String name,
        String normalizedName,
        UUID firstLocationId,
        UUID secondLocationId
    ) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO api_keys (
                id, integration_id, tenant_id, name, normalized_name, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            integrationId,
            tenantId,
            name,
            normalizedName,
            Instant.now()
        );
        jdbcTemplate.update(
            "INSERT INTO api_key_location_access (api_key_id, location_id, tenant_id) VALUES (?, ?, ?)",
            id,
            firstLocationId,
            tenantId
        );
        if (secondLocationId != null) {
            jdbcTemplate.update(
                "INSERT INTO api_key_location_access (api_key_id, location_id, tenant_id) VALUES (?, ?, ?)",
                id,
                secondLocationId,
                tenantId
            );
        }
        return id;
    }

    private UUID insertSubscription(
        UUID integrationId,
        UUID tenantId,
        String name,
        String normalizedName,
        UUID firstLocationId,
        UUID secondLocationId
    ) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO webhook_subscriptions (
                id, integration_id, tenant_id, name, normalized_name, destination_url,
                status, created_at, updated_at, last_enabled_at
            ) VALUES (?, ?, ?, ?, ?, 'https://example.com/webhook', 'ENABLED', ?, ?, ?)
            """,
            id,
            integrationId,
            tenantId,
            name,
            normalizedName,
            now,
            now,
            now
        );
        jdbcTemplate.update(
            """
            INSERT INTO webhook_subscription_location_access (
                subscription_id, location_id, tenant_id
            ) VALUES (?, ?, ?)
            """,
            id,
            firstLocationId,
            tenantId
        );
        if (secondLocationId != null) {
            jdbcTemplate.update(
                """
                INSERT INTO webhook_subscription_location_access (
                    subscription_id, location_id, tenant_id
                ) VALUES (?, ?, ?)
                """,
                id,
                secondLocationId,
                tenantId
            );
        }
        return id;
    }

    private Object value(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM " + table + " WHERE id = ?",
            Object.class,
            id
        );
    }

    private record IntegrationResources(
        UUID singleLocationKeyId,
        UUID multiLocationKeyId,
        UUID singleLocationSubscriptionId,
        UUID multiLocationSubscriptionId
    ) {
    }
}
