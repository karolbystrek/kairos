package pl.karolbystrek.kairos.api.account.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountProvisioningServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2026-07-20T12:00:00Z");
    @Autowired
    private AccountProvisioningService provisioningService;

    @Autowired
    private AccountInvitationService invitationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsOnlyAccountsManageableByTheCurrentAdministratorOrManager() {
        var tenantId = insertTenant();
        var firstLocationId = insertLocation(tenantId);
        var secondLocationId = insertLocation(tenantId);
        var administratorId = insertAccount(tenantId, "list.admin", TenantRole.ADMIN, AccountStatus.ENABLED);
        var managerId = insertAccount(tenantId, "list.manager", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(managerId, tenantId, firstLocationId, AssignmentRole.MANAGER);
        var ownOperatorId = insertAccount(tenantId, "list.own", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(ownOperatorId, tenantId, firstLocationId, AssignmentRole.OPERATOR);
        var otherOperatorId = insertAccount(tenantId, "list.other", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(otherOperatorId, tenantId, secondLocationId, AssignmentRole.OPERATOR);

        assertThat(provisioningService.listManageable(
            principal(administratorId, tenantId, TenantRole.ADMIN)
        ))
            .extracting(account -> account.email())
            .containsExactly("list.manager@example.com", "list.other@example.com", "list.own@example.com");
        assertThat(provisioningService.listManageable(
            principal(managerId, tenantId, TenantRole.MEMBER)
        ))
            .extracting(account -> account.email())
            .containsExactly("list.own@example.com");
        assertThatThrownBy(() -> provisioningService.listManageable(
            principal(ownOperatorId, tenantId, TenantRole.MEMBER)
        )).isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void disablingManagedAccountRevokesItsSessionsAndPendingInvitations() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var administratorId = insertAccount(tenantId, "admin.revoke", TenantRole.ADMIN, AccountStatus.ENABLED);
        var managerId = insertAccount(tenantId, "manager.revoke", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(managerId, tenantId, locationId, AssignmentRole.MANAGER);
        var invitation = invitationService.create(
            principal(managerId, tenantId, TenantRole.MEMBER),
            locationId,
            AssignmentRole.OPERATOR
        );
        var sessionId = insertSession(managerId);
        var administratorSession = insertSession(administratorId);

        var disabled = provisioningService.updateStatus(
            principal(administratorId, tenantId, TenantRole.ADMIN),
            managerId,
            AccountStatus.DISABLED
        );

        assertThat(disabled.status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(accountRepository.findById(managerId).orElseThrow().getStatus())
            .isEqualTo(AccountStatus.DISABLED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT authentication_cutoff IS NOT NULL FROM accounts WHERE id = ?",
            Boolean.class,
            managerId
        )).isTrue();
        assertThat(sessionExists(sessionId)).isFalse();
        assertThat(sessionExists(administratorSession)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
            "SELECT state, revocation_reason FROM account_invitations WHERE id = ?",
            invitation.invitation().id()
        )).containsEntry("state", "REVOKED")
            .containsEntry("revocation_reason", "ISSUER_DISABLED");
    }

    @Test
    void managerChangesStatusOnlyForOperatorsAtItsOwnLocation() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var otherLocationId = insertLocation(tenantId);
        var managerId = insertAccount(tenantId, "status.manager", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(managerId, tenantId, locationId, AssignmentRole.MANAGER);
        var ownOperatorId = insertAccount(tenantId, "own.operator", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(ownOperatorId, tenantId, locationId, AssignmentRole.OPERATOR);
        var otherOperatorId = insertAccount(tenantId, "other.operator", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(otherOperatorId, tenantId, otherLocationId, AssignmentRole.OPERATOR);
        var peerManagerId = insertAccount(tenantId, "peer.manager", TenantRole.MEMBER, AccountStatus.ENABLED);
        insertAssignment(peerManagerId, tenantId, locationId, AssignmentRole.MANAGER);
        var manager = principal(managerId, tenantId, TenantRole.MEMBER);

        assertThat(provisioningService.updateStatus(manager, ownOperatorId, AccountStatus.DISABLED).status())
            .isEqualTo(AccountStatus.DISABLED);
        assertThatThrownBy(() -> provisioningService.updateStatus(
            manager, otherOperatorId, AccountStatus.DISABLED
        )).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> provisioningService.updateStatus(
            manager, peerManagerId, AccountStatus.DISABLED
        )).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deletingManagedAccountArchivesItAndRevokesItsSessionsAndInvitations() {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var administratorId = insertAccount(
            tenantId,
            "delete.admin",
            TenantRole.ADMIN,
            AccountStatus.ENABLED
        );
        var managerId = insertAccount(
            tenantId,
            "delete.manager",
            TenantRole.MEMBER,
            AccountStatus.ENABLED
        );
        insertAssignment(managerId, tenantId, locationId, AssignmentRole.MANAGER);
        var invitation = invitationService.create(
            principal(managerId, tenantId, TenantRole.MEMBER),
            locationId,
            AssignmentRole.OPERATOR
        );
        var sessionId = insertSession(managerId);
        var administratorSession = insertSession(administratorId);
        var administrator = principal(
            administratorId,
            tenantId,
            TenantRole.ADMIN
        );

        provisioningService.delete(administrator, managerId);
        provisioningService.delete(administrator, managerId);

        assertThat(jdbcTemplate.queryForMap(
            "SELECT status, authentication_cutoff, archived_at FROM accounts WHERE id = ?",
            managerId
        )).containsEntry("status", "ARCHIVED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT archived_at IS NOT NULL FROM accounts WHERE id = ?",
            Boolean.class,
            managerId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT authentication_cutoff IS NOT NULL FROM accounts WHERE id = ?",
            Boolean.class,
            managerId
        )).isTrue();
        assertThat(sessionExists(sessionId)).isFalse();
        assertThat(sessionExists(administratorSession)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
            "SELECT state, revocation_reason FROM account_invitations WHERE id = ?",
            invitation.invitation().id()
        )).containsEntry("state", "REVOKED")
            .containsEntry("revocation_reason", "ISSUER_ARCHIVED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM location_assignments WHERE account_id = ?",
            Integer.class,
            managerId
        )).isOne();
        assertThat(provisioningService.listManageable(administrator)).isEmpty();
        assertThatThrownBy(() -> provisioningService.updateStatus(
            administrator,
            managerId,
            AccountStatus.ENABLED
        )).isInstanceOf(AccountNotFoundException.class);
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        return tenantId;
    }

    private UUID insertLocation(UUID tenantId) {
        var locationId = UUID.randomUUID();
        var name = "Test location " + locationId;
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
            locationId,
            tenantId,
            name,
            name.toLowerCase(java.util.Locale.ROOT),
            name.toLowerCase(java.util.Locale.ROOT)
        );
        return locationId;
    }

    private UUID insertAccount(
        UUID tenantId,
        String emailPrefix,
        TenantRole tenantRole,
        AccountStatus status
    ) {
        var accountId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            emailPrefix + "@example.com",
            tenantRole.name(),
            status.name(),
            Timestamp.from(FIXTURE_TIME),
            Timestamp.from(FIXTURE_TIME)
        );
        return accountId;
    }

    private void insertAssignment(
        UUID accountId,
        UUID tenantId,
        UUID locationId,
        AssignmentRole role
    ) {
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
            Timestamp.from(FIXTURE_TIME),
            Timestamp.from(FIXTURE_TIME)
        );
    }

    private String insertSession(UUID accountId) {
        var sessionId = UUID.randomUUID().toString();
        var now = Instant.now().toEpochMilli();
        jdbcTemplate.update(
            """
            INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,
                                        max_inactive_interval, expiry_time, principal_name)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID().toString(), sessionId, now, now, 30 * 86400,
            now + 30L * 86400 * 1000, accountId.toString()
        );
        return sessionId;
    }

    private boolean sessionExists(String sessionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) > 0 FROM spring_session WHERE session_id = ?",
            Boolean.class,
            sessionId
        );
    }

    private static StaffPrincipal principal(UUID accountId, UUID tenantId, TenantRole role) {
        return new StaffPrincipal(accountId, tenantId, role);
    }
}
