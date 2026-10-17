package pl.karolbystrek.kairos.api.account.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountInvitationConcurrencyIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private AccountInvitationService invitationService;

    @Autowired
    private LocationService locationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Test
    void refusesToInviteAnEmailThatAlreadyHasAnAccount() {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var existing = jdbcTemplate.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class,
            tenant.manager().accountId());

        assertThatThrownBy(() -> invitationService.create(tenant.administrator(), tenant.firstLocationId(),
            AssignmentRole.OPERATOR, existing)).isInstanceOf(AccountConflictException.class);
    }

    @Test
    void atomicRedemptionRejectsAnEmailOutsideTheInvitation() throws Exception {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "invited-" + UUID.randomUUID() + "@example.com";
        var created = invitationService.create(tenant.administrator(), tenant.firstLocationId(),
            AssignmentRole.OPERATOR, email);
        var differentEmail = "different-" + UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> invitationService.redeem(hash(created.token()), differentEmail, differentEmail))
            .isInstanceOf(RegistrationValidationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM account_invitations WHERE id = ?",
            String.class, created.invitation().id())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?",
            Integer.class, differentEmail)).isZero();
    }

    @Test
    void concurrentRedemptionCreatesExactlyOneAccountAndConsumesInvitationOnce() throws Exception {
        var tenantId = UUID.randomUUID();
        var locationId = UUID.randomUUID();
        var administratorId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
            locationId,
            tenantId,
            "Concurrent location",
            "concurrent location"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            administratorId,
            UUID.randomUUID().toString(),
            tenantId,
            "concurrent-invitation-admin-" + administratorId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
        );
        var emailPrefix = "concurrent-redeemer-" + UUID.randomUUID();
        var created = invitationService.create(
            new StaffPrincipal(administratorId, tenantId, TenantRole.ADMIN),
            locationId,
            AssignmentRole.OPERATOR,
            emailPrefix + "@example.com"
        );
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix
            ));
            var second = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix
            ));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE email LIKE ?",
            Integer.class,
            emailPrefix + "%"
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM account_invitations WHERE id = ?",
            String.class,
            created.invitation().id()
        )).isEqualTo("REDEEMED");
    }

    @Test
    void concurrentLocationDisableAndRedemptionFinishWithNoEnabledNewMember() throws Exception {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "location-race-" + UUID.randomUUID() + "@example.com";
        var created = invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var redemption = executor.submit(() -> {
                start.await();
                try {
                    invitationService.redeem(hash(created.token()), email, email);
                } catch (AccountInvitationUnavailableException unavailable) {
                    assertThat(unavailable.reason()).isEqualTo(AccountInvitationUnavailableException.Reason.REVOKED);
                }
                return null;
            });
            var disable = executor.submit(() -> {
                start.await();
                locationService.updateStatus(tenant.administrator(), tenant.firstLocationId(),
                    LocationStatus.DISABLED);
                return null;
            });
            start.countDown();
            redemption.get(15, TimeUnit.SECONDS);
            disable.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ? AND status = 'ENABLED'",
            Integer.class, email)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM account_invitations WHERE id = ?",
            String.class, created.invitation().id())).isIn("REDEEMED", "REVOKED");
    }

    private static String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean redeemAfterStart(CountDownLatch start, String token, String emailPrefix)
        throws Exception {
        start.await();
        try {
            invitationService.redeem(
                hash(token),
                emailPrefix + "@example.com",
                emailPrefix
            );
            return true;
        }
        catch (AccountInvitationUnavailableException exception) {
            assertThat(exception.reason())
                .isEqualTo(AccountInvitationUnavailableException.Reason.REDEEMED);
            return false;
        }
    }
    @Test
    void replacingAndResendingInvitationsInvalidatePreviousLinksAndKeepHistory() {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "replacement-" + UUID.randomUUID() + "@example.com";
        var first = invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
        assertThatThrownBy(() -> invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email))
            .isInstanceOf(AccountConflictException.class);
        var replacement = invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email, true);
        assertThatThrownBy(() -> invitationService.preview(first.token())).isInstanceOf(AccountInvitationUnavailableException.class);
        var resent = invitationService.resend(tenant.administrator(), replacement.invitation().id(), true);
        assertThatThrownBy(() -> invitationService.preview(replacement.token())).isInstanceOf(AccountInvitationUnavailableException.class);
        assertThat(invitationService.preview(resent.token()).email()).isEqualTo(email);
        assertThat(invitationService.history(tenant.administrator()))
            .extracting(invitation -> invitation.state().name()).containsExactly("PENDING", "REPLACED", "REPLACED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account_invitations WHERE tenant_id=? AND email=? AND state='PENDING'",
            Integer.class, tenant.administrator().tenantId(), email)).isEqualTo(1);
    }

    @Test
    void managerCannotReplaceHiddenInvitationEvenWhenItHasExpired() {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "hidden-" + UUID.randomUUID() + "@example.com";
        var previous = invitationService.create(tenant.administrator(), tenant.secondLocationId(), AssignmentRole.OPERATOR, email);
        for (var expired : List.of(false, true)) {
            if (expired) {
                jdbcTemplate.update("UPDATE account_invitations SET created_at=current_timestamp-interval '8 days', updated_at=current_timestamp-interval '8 days', expires_at=current_timestamp-interval '1 day' WHERE id=?",
                    previous.invitation().id());
            }
            assertThatThrownBy(() -> invitationService.create(tenant.manager(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email, true))
                .isInstanceOf(StaffAccessDeniedException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT state FROM account_invitations WHERE id=?", String.class,
                previous.invitation().id())).isEqualTo("PENDING");
            assertThat(jdbcTemplate.queryForObject("SELECT d.state FROM email_deliveries d JOIN account_invitations i ON i.delivery_id=d.id WHERE i.id=?",
                String.class, previous.invitation().id())).isEqualTo("QUEUED");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account_invitations WHERE tenant_id=? AND email=?",
            Integer.class, tenant.tenantId(), email)).isEqualTo(1);
    }

    @Test
    void managerHistoryIncludesAdminIssuedOperatorsOnlyAtOwnEnabledLocation() {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var first = invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR,
            "visible-" + UUID.randomUUID() + "@example.com");
        invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.MANAGER,
            "manager-" + UUID.randomUUID() + "@example.com");
        invitationService.create(tenant.administrator(), tenant.secondLocationId(), AssignmentRole.OPERATOR,
            "hidden-" + UUID.randomUUID() + "@example.com");
        var history = invitationService.history(tenant.manager());
        assertThat(history).hasSize(1);
        assertThat(history).extracting(invitation -> invitation.id()).containsExactly(first.invitation().id());
        assertThat(history.getFirst().issuedByEmail()).isEqualTo(first.invitation().issuedByEmail());
        assertThat(invitationService.history(tenant.administrator())).hasSize(3);
    }

    @Test
    void concurrentIssuanceHasOnePendingInvitationAndConfirmationIsRequiredForReplacement() throws Exception {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "concurrent-" + UUID.randomUUID() + "@example.com";
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var calls = IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await();
                try {
                    invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
                    return "created";
                } catch (AccountConflictException exception) { return "conflict"; }
            })).toList();
            start.countDown();
            assertThat(List.of(calls.get(0).get(10, TimeUnit.SECONDS), calls.get(1).get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("created", "conflict");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account_invitations WHERE tenant_id=? AND email=? AND state='PENDING'",
            Integer.class, tenant.tenantId(), email)).isEqualTo(1);
    }

}
