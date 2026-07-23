package pl.karolbystrek.kairos.api.authentication.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.AccountProvisioningService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidRefreshCredentialException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RefreshCredentialReuseException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.RefreshSessionRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthenticationSessionServiceIntegrationTests {

    @Autowired
    private AuthenticationSessionService sessionService;

    @Autowired
    private RefreshCredentialService credentialService;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private AccountProvisioningService provisioningService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StaffPrincipal principal;

    @BeforeEach
    void createActiveAdministrator() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update("INSERT INTO tenants (id, name) VALUES (?, ?)", tenantId, "Session tenant");
        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id, tenant_id, username, display_name, tenant_role, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?)
            """,
            accountId,
            tenantId,
            "session-" + accountId,
            "Session administrator",
            now,
            now
        );
        principal = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
    }

    @Test
    void rotatesWithoutPersistingTheRawCredentialAndRevokesTheFamilyOnReplay() {
        var initial = sessionService.start(principal);
        var initialRow = sessionRepository.findReferenceByRefreshTokenHash(
            credentialService.hash(initial.refreshCredential())
        ).flatMap(reference -> sessionRepository.findById(reference.getId())).orElseThrow();

        assertThat(initialRow.getRefreshTokenHash()).isNotEqualTo(initial.refreshCredential());
        assertThat(initialRow.getId()).isEqualTo(initialRow.getTokenFamilyId());

        var replacement = sessionService.rotate(initial.refreshCredential());
        var consumedRow = sessionRepository.findById(initialRow.getId()).orElseThrow();
        var replacementRow = sessionRepository.findReferenceByRefreshTokenHash(
            credentialService.hash(replacement.refreshCredential())
        ).flatMap(reference -> sessionRepository.findById(reference.getId())).orElseThrow();
        assertThat(consumedRow.getRevokedAt()).isNotNull();
        assertThat(consumedRow.getReplacedById()).isEqualTo(replacementRow.getId());
        assertThat(replacementRow.getTokenFamilyId()).isEqualTo(initialRow.getId());
        assertThat(replacementRow.getExpiresAt()).isEqualTo(initialRow.getExpiresAt());

        assertThatThrownBy(() -> sessionService.rotate(initial.refreshCredential()))
            .isInstanceOf(RefreshCredentialReuseException.class);
        assertThat(sessionRepository.findAll().stream()
            .filter(session -> session.getTokenFamilyId().equals(initialRow.getTokenFamilyId())))
            .allMatch(session -> session.getRevokedAt() != null);
    }

    @Test
    void aConcurrentDuplicateRefreshFailsClosedAndLeavesNoActiveFamilyCredential() throws Exception {
        var initial = sessionService.start(principal);

        var first = CompletableFuture.supplyAsync(() -> rotateOutcome(initial.refreshCredential()));
        var second = CompletableFuture.supplyAsync(() -> rotateOutcome(initial.refreshCredential()));
        var outcomes = java.util.List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)
        );

        assertThat(outcomes).containsExactlyInAnyOrder(RotationOutcome.ROTATED, RotationOutcome.REUSE_REJECTED);
        var familyId = sessionRepository.findAll().stream()
            .filter(session -> session.getAccountId().equals(principal.accountId()))
            .findFirst()
            .orElseThrow()
            .getTokenFamilyId();
        assertThat(sessionRepository.findAll().stream()
            .filter(session -> session.getTokenFamilyId().equals(familyId)))
            .allMatch(session -> session.getRevokedAt() != null);
    }

    @Test
    void logoutRevokesOnlyTheCurrentSessionWhileLogoutAllRevokesEverySession() {
        var first = sessionService.start(principal);
        var second = sessionService.start(principal);

        sessionService.logout(principal, first.refreshCredential());
        assertThat(activeSessionCount()).isEqualTo(1);

        sessionService.logoutAll(principal);
        assertThat(activeSessionCount()).isZero();
        assertThatThrownBy(() -> sessionService.rotate(second.refreshCredential()))
            .isInstanceOf(InvalidRefreshCredentialException.class);
    }

    @Test
    void disablingAnAccountRacingWithRefreshLeavesNoActiveCredential() throws Exception {
        var locationId = UUID.randomUUID();
        var operatorId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name) VALUES (?, ?, ?)",
            locationId,
            principal.tenantId(),
            "Race location"
        );
        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id, tenant_id, username, password_hash, display_name,
                tenant_role, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 'MEMBER', 'ACTIVE', ?, ?)
            """,
            operatorId,
            principal.tenantId(),
            "race-operator-" + operatorId,
            "fixture-password-hash",
            "Race operator",
            now,
            now
        );
        jdbcTemplate.update(
            """
            INSERT INTO location_assignments (
                account_id, location_id, tenant_id, role, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            operatorId,
            locationId,
            principal.tenantId(),
            AssignmentRole.OPERATOR.name(),
            now,
            now
        );

        var operator = new StaffPrincipal(operatorId, principal.tenantId(), TenantRole.MEMBER);
        var initial = sessionService.start(operator);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        var rotation = CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                return sessionService.rotate(initial.refreshCredential());
            }
            catch (InvalidRefreshCredentialException exception) {
                return null;
            }
        });
        var disablement = CompletableFuture.runAsync(() -> {
            ready.countDown();
            await(start);
            provisioningService.changeStatus(principal, operatorId, AccountStatus.DISABLED);
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        var rotated = rotation.get(10, TimeUnit.SECONDS);
        disablement.get(10, TimeUnit.SECONDS);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM accounts WHERE id = ?",
            String.class,
            operatorId
        )).isEqualTo(AccountStatus.DISABLED.name());
        assertThat(sessionRepository.findAll().stream()
            .filter(session -> session.getAccountId().equals(operatorId)))
            .isNotEmpty()
            .allMatch(session -> session.getRevokedAt() != null);
        if (rotated != null) {
            assertThatThrownBy(() -> sessionService.rotate(rotated.refreshCredential()))
                .isInstanceOf(InvalidRefreshCredentialException.class);
        }
    }

    private RotationOutcome rotateOutcome(String credential) {
        try {
            sessionService.rotate(credential);
            return RotationOutcome.ROTATED;
        }
        catch (RefreshCredentialReuseException exception) {
            return RotationOutcome.REUSE_REJECTED;
        }
    }

    private long activeSessionCount() {
        return sessionRepository.findAll().stream()
            .filter(session -> session.getAccountId().equals(principal.accountId()))
            .filter(session -> session.getRevokedAt() == null)
            .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating a concurrency test", exception);
        }
    }

    private enum RotationOutcome {
        ROTATED,
        REUSE_REJECTED
    }
}
