package pl.karolbystrek.kairos.api.integration.testsupport;

import lombok.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class IntegrationTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public IntegrationTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TenantFixture createTenant() {
        var tenantId = UUID.randomUUID();
        var firstLocationId = UUID.randomUUID();
        var secondLocationId = UUID.randomUUID();
        var now = Instant.parse("2026-07-26T10:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            """
                INSERT INTO locations (
                    id, tenant_id, name, normalized_name,
                    created_at, updated_at, last_enabled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)
                """,
                firstLocationId,
                tenantId,
                "First location",
                "first location",
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                secondLocationId,
                tenantId,
                "Second location",
                "second location",
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        var administrator = createAccount(tenantId, TenantRole.ADMIN, null, null);
        var manager = createAccount(
                tenantId,
                TenantRole.MEMBER,
                firstLocationId,
                "MANAGER"
        );
        return new TenantFixture(
                tenantId,
                firstLocationId,
                secondLocationId,
                administrator,
                manager
        );
    }

    private StaffPrincipal createAccount(
            UUID tenantId,
            TenantRole tenantRole,
            UUID locationId,
            String assignmentRole
    ) {
        var accountId = UUID.randomUUID();
        var now = Instant.parse("2026-07-26T10:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            "integration-test-" + accountId + "@example.com",
            tenantRole.name(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (locationId != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO location_assignments (
                        account_id, location_id, tenant_id, role, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    accountId,
                    locationId,
                    tenantId,
                    assignmentRole,
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
        }
        return new StaffPrincipal(accountId, tenantId, tenantRole);
    }

    public record TenantFixture(
            @NonNull UUID tenantId,
            @NonNull UUID firstLocationId,
            @NonNull UUID secondLocationId,
            @NonNull StaffPrincipal administrator,
            @NonNull StaffPrincipal manager
    ) {
    }
}
