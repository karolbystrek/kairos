package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import pl.karolbystrek.kairos.api.authentication.application.RegistrationVerificationService;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired private MockMvc mvc;
    private final JdbcTemplate database = PostgresTestDatabase.ownerDatabase();
    @Autowired private JdbcTemplate runtimeDatabase;
    @Autowired private AccountInvitationService invitations;
    @Autowired private RegistrationVerificationService verification;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void registrationCreatesNoTenantUntilCodeThenEstablishesDurableSecureSession() throws Exception {
        var email = email();
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationRequired").value(true)).andReturn();
        useVerificationCode(email, "123456");
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
        var pendingCookie = pending.getResponse().getCookie("__Host-session");
        mvc.perform(getApi("/auth/v1/me").cookie(pendingCookie)).andExpect(status().isUnauthorized());
        var result = mvc.perform(csrf(postApi("/auth/v1/verification").cookie(pendingCookie).content("{\"code\":\"123456\"}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("ADMIN"))
            .andExpect(jsonPath("$.username").doesNotExist())
            .andExpect(jsonPath("$.verificationRequired").doesNotExist())
            .andExpect(cookie().httpOnly("__Host-session", true)).andExpect(cookie().secure("__Host-session", true))
            .andExpect(cookie().sameSite("__Host-session", "Lax")).andExpect(cookie().path("__Host-session", "/"))
            .andExpect(cookie().maxAge("__Host-session", 30 * 86400)).andReturn();
        var locations = database.queryForList("""
            SELECT name, normalized_name, status, time_zone FROM locations
            WHERE tenant_id = (SELECT tenant_id FROM accounts WHERE email = ?)
            """, email);
        assertThat(locations).isEmpty();
        assertThat(runtimeDatabase.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
        var cookie = result.getResponse().getCookie("__Host-session");
        assertThat(cookie.getDomain()).isNull();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isOne();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = (SELECT CAST(id AS VARCHAR) FROM accounts WHERE email = ?)", Integer.class, email)).isOne();
        assertThat(database.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE principal_name = (SELECT CAST(id AS VARCHAR) FROM accounts WHERE email = ?)", Integer.class, email)).isEqualTo(30 * 86400);
        mvc.perform(getApi("/auth/v1/me").cookie(cookie)).andExpect(status().isOk())
            .andExpect(cookie().value("__Host-session", cookie.getValue()))
            .andExpect(cookie().maxAge("__Host-session", 30 * 86400));
        assertThat(database.queryForObject("SELECT password_hash FROM staff_identities WHERE email = ?", String.class, email))
            .isNotBlank().doesNotContain("password-12345");
    }

    @Test
    void repeatedFailedSignInsThrottleOnlyThatAccount() throws Exception {
        var email = email();
        var other = email();
        register(email);
        register(other);
        var wrong = "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"wrong-password-1\"}";
        for (var attempt = 0; attempt < 10; attempt++)
            mvc.perform(csrf(postApi("/auth/v1/login").content(wrong))).andExpect(status().isUnauthorized());
        // Even the correct password is refused (and unchecked) while the account is throttled.
        mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}")))
            .andExpect(status().isTooManyRequests());
        login(other);
    }

    @Test
    void invalidRegistrationReturnsFieldErrorsBeforeProviderProvisioning() throws Exception {
        var email = email();
        mvc.perform(csrf(postApi("/tenant-registrations/v1")
            .content(registration("invalid-email", null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        mvc.perform(csrf(postApi("/tenant-registrations/v1")
            .content(registration(email, null).replace("password-12345", "short"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.password").value("Use at least 12 characters."));
        mvc.perform(csrf(postApi("/tenant-registrations/v1")
            .content(registration(email, null).replace("passwordConfirmation\":\"password-12345", "passwordConfirmation\":\"different"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.passwordConfirmation").value("Passwords must match."));
        verify(identities, never()).createUnverifiedUser(anyString(), anyString());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void archivedIdentityStillHasAnEmailFieldError() throws Exception {
        var email = email();
        register(email);
        database.update("UPDATE accounts SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP WHERE email = ?", email);
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }

    @Test
    void providerEmailConflictIsAttachedToEmailWithoutExposingProviderDetails() throws Exception {
        var email = email();
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "private provider details"))
            .when(identities).createUnverifiedUser(eq(email), anyString());
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
            .andExpect(content().string(Matchers.not(Matchers.containsString("private provider"))));
    }

    @Test
    void incompleteOnboardingSurvivesSignInAndFirstLocationIsCreatedSeparately() throws Exception {
        var email = email();
        var session = register(email);
        mvc.perform(getApi("/locations/v1").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(csrf(postApi("/auth/v1/logout").cookie(session))).andExpect(status().isNoContent());
        session = login(email);
        mvc.perform(getApi("/locations/v1").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(csrf(postApi("/locations/v1").cookie(session).content("{\"name\":\"  First location  \",\"timeZone\":\"Europe/Warsaw\"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("First location"))
            .andExpect(jsonPath("$.timeZone").value("Europe/Warsaw"))
            .andExpect(jsonPath("$.status").value("ENABLED"));
        mvc.perform(getApi("/locations/v1").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void logoutDoesNotAffectOtherDevices() throws Exception {
        var email = email();
        var first = register(email);
        var second = login(email);
        mvc.perform(csrf(postApi("/auth/v1/logout").cookie(first))).andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(getApi("/auth/v1/me").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void expiredSessionIsRejectedAndActivityExtendsExpiry() throws Exception {
        var email = email();
        var session = register(email);
        var principal = database.queryForObject("SELECT CAST(id AS VARCHAR) FROM accounts WHERE email = ?", String.class, email);
        database.update("UPDATE spring_session SET last_access_time = last_access_time - 86400000, expiry_time = expiry_time - 86400000 WHERE principal_name = ?", principal);
        var old = database.queryForObject("SELECT expiry_time FROM spring_session WHERE principal_name = ?", Long.class, principal);
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isOk());
        assertThat(database.queryForObject("SELECT expiry_time FROM spring_session WHERE principal_name = ?", Long.class, principal)).isGreaterThan(old);
        database.update("UPDATE spring_session SET last_access_time = 0, expiry_time = 0 WHERE principal_name = ?", principal);
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotAccessEvenWithValidCredentials() throws Exception {
        var email = email();
        var session = register(email);
        database.update("UPDATE accounts SET status = 'DISABLED' WHERE email = ?", email);
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndEndsAllSessions() throws Exception {
        var email = email();
        var first = register(email);
        var second = login(email);
        var payload = "{\"currentPassword\":\"password-12345\",\"password\":\"new-password-123\",\"passwordConfirmation\":\"new-password-123\"}";
        mvc.perform(postApi("/auth/v1/password").cookie(first).content(payload)).andExpect(status().isForbidden());
        mvc.perform(csrf(postApi("/auth/v1/password").cookie(first).content(payload.replace("password-12345", "wrong-password-1"))))
            .andExpect(status().isBadRequest());
        mvc.perform(csrf(postApi("/auth/v1/password").cookie(first).content(payload.replace("new-password-123", "too-short"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").value("Use at least 12 characters."));
        mvc.perform(csrf(postApi("/auth/v1/password").cookie(first).content(payload))).andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(getApi("/auth/v1/me").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}")))
            .andExpect(status().isUnauthorized());
        login(email, "new-password-123");
    }

    @Test
    void invalidInvitationIsRejectedBeforeProviderCreationAndSignedInRegistrationIsRejected() throws Exception {
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), "invalid"))))
            .andExpect(status().isNotFound());
        verify(identities, never()).createUnverifiedUser(anyString(), anyString());
        var session = register(email());
        mvc.perform(csrf(postApi("/tenant-registrations/v1").cookie(session).content(registration(email(), null))))
            .andExpect(status().isForbidden());
    }

    @Test
    void identityIsRolledBackWhenInvitationBecomesUnavailableBeforeCommit() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var email = email();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.MANAGER, email);
        doAnswer(call -> {
            var transaction = new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> invitations.revoke(tenant.administrator(), invitation.invitation().id()));
            return call.callRealMethod();
        }).when(identities).markEmailVerified(anyString(), eq(email));
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isGone());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM staff_identities WHERE email = ?", Integer.class, email)).isZero();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void wrongAttemptsPersistAcrossSessionsAndResendHasCooldown() throws Exception {
        var email = email();
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
        useVerificationCode(email, "123456");
        mvc.perform(csrf(postApi("/auth/v1/verification/resend").cookie(pending)))
            .andExpect(status().isTooManyRequests());
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(csrf(postApi("/auth/v1/verification").cookie(pending).content("{\"code\":\"000000\"}")))
                .andExpect(status().isBadRequest());
        }
        var resumed = login(email);
        mvc.perform(getApi("/auth/v1/verification").cookie(resumed)).andExpect(status().isOk());
        mvc.perform(csrf(postApi("/auth/v1/verification").cookie(resumed).content("{\"code\":\"123456\"}")))
            .andExpect(status().isBadRequest());
        verify(identities, never()).verifyEmail(anyString(), eq("123456"));
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE email=?", email);
        mvc.perform(csrf(postApi("/auth/v1/verification/resend").cookie(resumed))).andExpect(status().isOk());
        useVerificationCode(email, "123456");
        mvc.perform(csrf(postApi("/auth/v1/verification").cookie(resumed).content("{\"code\":\"123456\"}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("ADMIN"));
    }

    @Test
    void expiredVerificationCodeIsRejected() throws Exception {
        var email = email();
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
        useVerificationCode(email, "123456");
        database.update("UPDATE staff_identities SET verification_code_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE email=?", email);
        mvc.perform(csrf(postApi("/auth/v1/verification").cookie(pending).content("{\"code\":\"123456\"}")))
            .andExpect(status().isBadRequest());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isZero();
    }

    @Test
    void abandonedUnverifiedIdentityCanBeReplacedButVerifiedIdentityCannot() throws Exception {
        var email = email();
        var abandoned = identities.createUser(email, "password-12345");
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationRequired").value(true));
        verify(identities).deleteUser(abandoned);
        var protectedEmail = email();
        var verified = identities.createUser(protectedEmail, "password-12345");
        identities.markEmailVerified(verified, protectedEmail);
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(protectedEmail, null))))
            .andExpect(status().isConflict());
        verify(identities, never()).deleteUser(verified);
    }

    @Test
    void replacementCancelsActualPendingDeliveryAndRemovesOldState() {
        var email = email();
        var old = verification.register(email, "password-12345").subject();
        var oldDelivery = database.queryForObject("SELECT delivery_id FROM pending_registrations WHERE subject=?", UUID.class, old);
        assertThat(catchThrowable(() -> verification.register(email, "password-12345")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(identities, never()).deleteUser(old);
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject=?", old);
        verification.register(email, "password-12345");
        verify(identities).deleteUser(old);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM pending_registrations WHERE subject=?", Integer.class, old)).isZero();
        assertThat(database.queryForMap("SELECT state,encrypted_payload,payload_nonce FROM email_deliveries WHERE id=?", oldDelivery))
            .containsEntry("state", "FAILED").containsEntry("encrypted_payload", null).containsEntry("payload_nonce", null);
    }

    @Test
    void concurrentVerificationPreventsReplacementFromDeletingCommittedIdentity() throws Exception {
        var email = email();
        var pending = verification.register(email, "password-12345");
        useVerificationCode(email, "123456");
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject=?", pending.subject());
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            verifying.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(identities).verifyEmail(pending.subject(), "123456");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accepted = executor.submit(() -> verification.verify(pending, "123456"));
            assertThat(verifying.await(5, TimeUnit.SECONDS)).isTrue();
            var replacement = executor.submit(() -> catchThrowable(
                () -> verification.register(email, "password-12345")));
            release.countDown();
            assertThat(accepted.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(replacement.get(5, TimeUnit.SECONDS)).isInstanceOf(RegistrationValidationException.class);
        }
        verify(identities, never()).deleteUser(pending.subject());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isOne();
    }

    @Test
    void invitationRegistrationVerifiesOnlyTheIntendedRecipient() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var email = email();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), invitation.token()))))
            .andExpect(status().isBadRequest());
        verify(identities, never()).markEmailVerified(anyString(), anyString());
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("MEMBER"))
            .andExpect(jsonPath("$.verificationRequired").doesNotExist());
        verify(identities).markEmailVerified(anyString(), eq(email));
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isOne();
    }

    @Test
    void passwordResetReplacesThePasswordEndsSessionsAndRevealsNoAccounts() throws Exception {
        var email = email();
        var session = register(email);
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email() + "\"}")))
            .andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(database.queryForObject("SELECT COUNT(*) FROM password_resets", Integer.class)).isZero();
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email.toUpperCase() + "\"}")))
            .andExpect(status().isAccepted()).andExpect(content().string(""));
        var firstHash = resetCodeHash(email);
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email + "\"}")))
            .andExpect(status().isAccepted());
        assertThat(resetCodeHash(email)).as("a resend within 60 seconds keeps the emailed code").isEqualTo(firstHash);
        useResetCode(email, "123456");
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "654321"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.code").isNotEmpty());
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "123456").replace("new-password-123", "too-short"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "123456"))))
            .andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}")))
            .andExpect(status().is4xxClientError());
        mvc.perform(getApi("/auth/v1/me").cookie(login(email, "new-password-123"))).andExpect(status().isOk());
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "123456"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetAllowsFiveWrongCodesThenRequiresANewCode() throws Exception {
        var email = email();
        register(email);
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email + "\"}"))).andExpect(status().isAccepted());
        useResetCode(email, "123456");
        for (var attempt = 0; attempt < 5; attempt++) {
            mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "000000"))))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "123456"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.code").value("Request a new code before trying again."));
        login(email);
    }

    @Test
    void passwordResetRecoversAnAccountWithoutStoredCredentials() throws Exception {
        var email = email();
        register(email);
        // Accounts created before API-owned credentials have no staff_identities row.
        database.update("DELETE FROM staff_identities WHERE email = ?", email);
        mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}")))
            .andExpect(status().is4xxClientError());
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email + "\"}"))).andExpect(status().isAccepted());
        useResetCode(email, "123456");
        mvc.perform(csrf(postApi("/auth/v1/password-reset/confirmation").content(reset(email, "123456"))))
            .andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(login(email, "new-password-123"))).andExpect(status().isOk());
    }

    @Test
    void disabledAccountsCannotResetTheirPassword() throws Exception {
        var email = email();
        register(email);
        database.update("UPDATE accounts SET status = 'DISABLED' WHERE email = ?", email);
        mvc.perform(csrf(postApi("/auth/v1/password-reset").content("{\"email\":\"" + email + "\"}"))).andExpect(status().isAccepted());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM password_resets", Integer.class)).isZero();
    }

    private String resetCodeHash(String email) {
        return database.queryForObject("SELECT r.code_hash FROM password_resets r JOIN accounts a ON a.provider_subject = r.subject WHERE a.email = ?",
            String.class, email);
    }
    // Codes are random; replace the stored hash with a known code.
    private void useResetCode(String email, String code) {
        database.update("""
            UPDATE password_resets r SET code_hash = encode(sha256(convert_to('password-reset:' || r.subject || ':' || ?, 'UTF8')), 'hex')
            FROM accounts a WHERE a.provider_subject = r.subject AND a.email = ?
            """, code, email);
    }
    private static String reset(String email, String code) {
        return "{\"email\":\"" + email + "\",\"code\":\"" + code
            + "\",\"password\":\"new-password-123\",\"passwordConfirmation\":\"new-password-123\"}";
    }

    private Cookie register(String email) throws Exception {
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
        useVerificationCode(email, "123456");
        return mvc.perform(csrf(postApi("/auth/v1/verification").cookie(pending).content("{\"code\":\"123456\"}")))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
    }
    private Cookie login(String email) throws Exception { return login(email, "password-12345"); }
    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
    }
    private static String email() { return UUID.randomUUID() + "@example.com"; }
    private static String registration(String email, String token) {
        return "{\"email\":\"" + email + "\",\"password\":\"password-12345\",\"passwordConfirmation\":\"password-12345\""
            + (token == null ? "" : ",\"token\":\"" + token + "\"") + "}";
    }
    private static MockHttpServletRequestBuilder postApi(String path) {
        return post("/api" + path).contextPath("/api").secure(true).contentType(MediaType.APPLICATION_JSON);
    }
    private static MockHttpServletRequestBuilder getApi(String path) {
        return get("/api" + path).contextPath("/api").secure(true);
    }
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) throws Exception {
        var bootstrap = mvc.perform(getApi("/auth/v1/csrf")).andReturn();
        var cookie = bootstrap.getResponse().getCookie("__Host-XSRF-TOKEN");
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }
}
