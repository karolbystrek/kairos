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
            .extracting(account -> account.username())
            .containsExactly("list.manager", "list.other", "list.own");
        assertThat(provisioningService.listManageable(
            principal(managerId, tenantId, TenantRole.MEMBER)
        ))
            .extracting(account -> account.username())
            .containsExactly("list.own");
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

        var disabled = provisioningService.updateStatus(
            principal(administratorId, tenantId, TenantRole.ADMIN),
            managerId,
            AccountStatus.DISABLED
        );

        assertThat(disabled.status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(accountRepository.findById(managerId).orElseThrow().getStatus())
            .isEqualTo(AccountStatus.DISABLED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
            Boolean.class,
            sessionId
        )).isTrue();
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
    void deletingManagedAccountArchivesItAndRemovesEveryAuthenticator() {
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
        jdbcTemplate.update(
            """
                INSERT INTO external_identities (
                    id, account_id, provider, subject, created_at, updated_at
                ) VALUES (?, ?, 'example', ?, ?, ?)
                """,
            UUID.randomUUID(),
            managerId,
            "subject-" + managerId,
            FIXTURE_TIME,
            FIXTURE_TIME
        );
        var administrator = principal(
            administratorId,
            tenantId,
            TenantRole.ADMIN
        );

        provisioningService.delete(administrator, managerId);
        provisioningService.delete(administrator, managerId);

        assertThat(jdbcTemplate.queryForMap(
            "SELECT status, password_hash, archived_at FROM accounts WHERE id = ?",
            managerId
        )).containsEntry("status", "ARCHIVED")
            .containsEntry("password_hash", null);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT archived_at IS NOT NULL FROM accounts WHERE id = ?",
            Boolean.class,
            managerId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM external_identities WHERE account_id = ?",
            Integer.class,
            managerId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
            Boolean.class,
            sessionId
        )).isTrue();
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            accountId,
            tenantId,
            username,
            username + "@example.com",
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
