package pl.karolbystrek.kairos.api.account.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountProvisioningServiceIntegrationTests {

    private static final Instant FIXTURE_TIME = Instant.parse("2026-07-20T12:00:00Z");
    private static final String INITIAL_PASSWORD = "SecurePass-12";

    @Autowired
    private AccountProvisioningService provisioningService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LocationAssignmentRepository assignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void administratorProvisionsNormalizedManagersAndOperatorsInsideItsTenant() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var administratorId = insertAccount(tenantId, "admin", TenantRole.ADMIN, AccountStatus.ACTIVE);
        var administrator = principal(administratorId, tenantId, TenantRole.ADMIN);

        var manager = provisioningService.provision(
            administrator,
            locationId,
            "  Shift.Manager  ",
            "  MANAGER@EXAMPLE.COM  ",
            INITIAL_PASSWORD,
            AssignmentRole.MANAGER
        );
        var operator = provisioningService.provision(
            administrator,
            locationId,
            "Counter.Device.1",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        );

        assertThat(manager.tenantId()).isEqualTo(tenantId);
        assertThat(manager.locationId()).isEqualTo(locationId);
        assertThat(manager.username()).isEqualTo("shift.manager");
        assertThat(manager.email()).isEqualTo("manager@example.com");
        assertThat(manager.role()).isEqualTo(AssignmentRole.MANAGER);
        assertThat(operator.role()).isEqualTo(AssignmentRole.OPERATOR);

        var persisted = accountRepository.findById(manager.id()).orElseThrow();
        assertThat(persisted.getTenantRole()).isEqualTo(TenantRole.MEMBER);
        assertThat(persisted.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(persisted.getPasswordHash()).isNotEqualTo(INITIAL_PASSWORD);
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, persisted.getPasswordHash())).isTrue();
        assertThat(assignmentRepository.findByIdAccountId(manager.id()).orElseThrow().getLocationId())
            .isEqualTo(locationId);
    }

    @Test
    void managerProvisionsOnlyOperatorsForItsOwnActiveLocation() {
        var tenantId = insertTenant();
        var assignedLocationId = insertLocation(tenantId);
        var otherLocationId = insertLocation(tenantId);
        var managerId = insertAccount(tenantId, "manager", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(managerId, tenantId, assignedLocationId, AssignmentRole.MANAGER, "ACTIVE");
        var manager = principal(managerId, tenantId, TenantRole.MEMBER);

        var operator = provisioningService.provision(
            manager,
            assignedLocationId,
            "device.one",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        );

        assertThat(operator.locationId()).isEqualTo(assignedLocationId);
        assertThat(operator.role()).isEqualTo(AssignmentRole.OPERATOR);
        assertThatThrownBy(() -> provisioningService.provision(
            manager,
            assignedLocationId,
            "another.manager",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.MANAGER
        )).isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> provisioningService.provision(
            manager,
            otherLocationId,
            "other.device",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        )).isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void operatorAndCrossTenantAdministratorCannotProvisionAccounts() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var operatorId = insertAccount(tenantId, "operator", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(operatorId, tenantId, locationId, AssignmentRole.OPERATOR, "ACTIVE");

        var otherTenantId = insertTenant();
        var otherLocationId = insertLocation(otherTenantId);
        var administratorId = insertAccount(tenantId, "admin", TenantRole.ADMIN, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> provisioningService.provision(
            principal(operatorId, tenantId, TenantRole.MEMBER),
            locationId,
            "operator.created",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        )).isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> provisioningService.provision(
            principal(administratorId, tenantId, TenantRole.ADMIN),
            otherLocationId,
            "cross.tenant",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        )).isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void suspendedManagerCannotProvisionAccounts() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var managerId = insertAccount(tenantId, "suspended.manager", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(managerId, tenantId, locationId, AssignmentRole.MANAGER, "SUSPENDED");

        assertThatThrownBy(() -> provisioningService.provision(
            principal(managerId, tenantId, TenantRole.MEMBER),
            locationId,
            "suspended.created",
            null,
            INITIAL_PASSWORD,
            AssignmentRole.OPERATOR
        )).isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void disablingManagedAccountRevokesItsRefreshSessions() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var administratorId = insertAccount(tenantId, "admin.revoke", TenantRole.ADMIN, AccountStatus.ACTIVE);
        var operatorId = insertAccount(tenantId, "device.revoke", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(operatorId, tenantId, locationId, AssignmentRole.OPERATOR, "ACTIVE");
        var sessionId = insertSession(operatorId);

        var disabled = provisioningService.changeStatus(
            principal(administratorId, tenantId, TenantRole.ADMIN),
            operatorId,
            AccountStatus.DISABLED
        );

        assertThat(disabled.status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(accountRepository.findById(operatorId).orElseThrow().getStatus())
            .isEqualTo(AccountStatus.DISABLED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
            Boolean.class,
            sessionId
        )).isTrue();
    }

    @Test
    void managerChangesStatusOnlyForOperatorsAtItsOwnLocation() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var otherLocationId = insertLocation(tenantId);
        var managerId = insertAccount(tenantId, "status.manager", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(managerId, tenantId, locationId, AssignmentRole.MANAGER, "ACTIVE");
        var ownOperatorId = insertAccount(tenantId, "own.operator", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(ownOperatorId, tenantId, locationId, AssignmentRole.OPERATOR, "ACTIVE");
        var otherOperatorId = insertAccount(tenantId, "other.operator", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(otherOperatorId, tenantId, otherLocationId, AssignmentRole.OPERATOR, "ACTIVE");
        var peerManagerId = insertAccount(tenantId, "peer.manager", TenantRole.MEMBER, AccountStatus.ACTIVE);
        insertAssignment(peerManagerId, tenantId, locationId, AssignmentRole.MANAGER, "ACTIVE");
        var manager = principal(managerId, tenantId, TenantRole.MEMBER);

        assertThat(provisioningService.changeStatus(manager, ownOperatorId, AccountStatus.DISABLED).status())
            .isEqualTo(AccountStatus.DISABLED);
        assertThatThrownBy(() -> provisioningService.changeStatus(
            manager, otherOperatorId, AccountStatus.DISABLED
        )).isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> provisioningService.changeStatus(
            manager, peerManagerId, AccountStatus.DISABLED
        )).isInstanceOf(StaffAccessDeniedException.class);
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
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

    private UUID insertAccount(
        UUID tenantId,
        String username,
        TenantRole tenantRole,
        AccountStatus status
    ) {
        var accountId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?)
                """,
            accountId,
            tenantId,
            username,
            "fixture-password-hash",
            tenantRole.name(),
            status.name(),
            FIXTURE_TIME,
            FIXTURE_TIME
        );
        return accountId;
    }

    private void insertAssignment(
        UUID accountId,
        UUID tenantId,
        UUID locationId,
        AssignmentRole role,
        String status
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO location_assignments (
                    account_id, location_id, tenant_id, role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            accountId,
            locationId,
            tenantId,
            role.name(),
            status,
            FIXTURE_TIME,
            FIXTURE_TIME
        );
    }

    private UUID insertSession(UUID accountId) {
        var sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sessions (
                    id, account_id, refresh_token_hash, token_family_id,
                    created_at, expires_at, last_used_at, revoked_at, replaced_by_id
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL)
                """,
            sessionId,
            accountId,
            "fixture-hash-" + sessionId,
            sessionId,
            FIXTURE_TIME,
            FIXTURE_TIME.plus(30, ChronoUnit.DAYS)
        );
        return sessionId;
    }

    private static StaffPrincipal principal(UUID accountId, UUID tenantId, TenantRole role) {
        return new StaffPrincipal(accountId, tenantId, role);
    }
}
