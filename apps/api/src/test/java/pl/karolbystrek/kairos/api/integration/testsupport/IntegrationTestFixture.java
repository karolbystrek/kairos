package pl.karolbystrek.kairos.api.integration.testsupport;

import lombok.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

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
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id) VALUES (?, ?), (?, ?)",
                firstLocationId,
                tenantId,
                secondLocationId,
                tenantId
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
                INSERT INTO accounts (
                    id, tenant_id, username, tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                accountId,
                tenantId,
                "integration-test-" + accountId,
                tenantRole.name(),
                now,
                now
        );
        if (locationId != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO location_assignments (
                        account_id, location_id, tenant_id, role, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    accountId,
                    locationId,
                    tenantId,
                    assignmentRole,
                    now,
                    now
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
