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
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.ProviderSession;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import pl.karolbystrek.kairos.api.authentication.application.RegistrationVerificationService;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.PendingUser;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.UserEmail;
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
        configure(email);
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationRequired").value(true)).andReturn();
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
        verify(identityProvider, atLeastOnce()).isValid(any());
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
        verify(identityProvider, never()).createUnverifiedUser(anyString(), anyString());
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
        when(identityProvider.createUnverifiedUser(eq(email), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "private provider details"));
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
        mvc.perform(csrf(postApi("/locations/v1").cookie(session).content("{\"name\":\"  First location  \"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("First location"))
            .andExpect(jsonPath("$.status").value("ENABLED"));
        mvc.perform(getApi("/locations/v1").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ordinaryLogoutDoesNotAffectOtherDevicesButLogoutAllDoes() throws Exception {
        var email = email();
        var first = register(email);
        var second = login(email);
        mvc.perform(csrf(postApi("/auth/v1/logout").cookie(first))).andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(getApi("/auth/v1/me").cookie(second)).andExpect(status().isOk());
        var third = login(email);
        mvc.perform(csrf(postApi("/auth/v1/logout-all").cookie(second))).andExpect(status().isNoContent());
        mvc.perform(getApi("/auth/v1/me").cookie(third)).andExpect(status().isUnauthorized());
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
    void providerOutagePreservesSessionButInvalidProviderStateEndsIt() throws Exception {
        var session = register(email());
        when(identityProvider.isValid(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isServiceUnavailable())
            .andExpect(cookie().doesNotExist("__Host-session"));
        doReturn(true).when(identityProvider).isValid(any());
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isOk());
        doReturn(false).when(identityProvider).isValid(any());
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isUnauthorized());
        doReturn(true).when(identityProvider).isValid(any());
        mvc.perform(getApi("/auth/v1/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotAccessEvenWithValidProviderSession() throws Exception {
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
        var payload = "{\"currentPassword\":\"old-password\",\"password\":\"new-password-123\",\"passwordConfirmation\":\"new-password-123\"}";
        mvc.perform(postApi("/auth/v1/password").cookie(first).content(payload)).andExpect(status().isForbidden());
        mvc.perform(csrf(postApi("/auth/v1/password").cookie(first).content(payload))).andExpect(status().isNoContent());
        verify(identityProvider).changePassword(email, "old-password", "new-password-123");
        mvc.perform(getApi("/auth/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(getApi("/auth/v1/me").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInvitationIsRejectedBeforeProviderCreationAndSignedInRegistrationIsRejected() throws Exception {
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), "invalid"))))
            .andExpect(status().isNotFound());
        verify(identityProvider, never()).createUnverifiedUser(anyString(), anyString());
        var session = register(email());
        mvc.perform(csrf(postApi("/tenant-registrations/v1").cookie(session).content(registration(email(), null))))
            .andExpect(status().isForbidden());
    }

    @Test
    void providerIdentityIsCleanedUpWhenInvitationBecomesUnavailableBeforeCommit() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var email = email();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.MANAGER, email);
        configure(email);
        doAnswer(call -> {
            var transaction = new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> invitations.revoke(tenant.administrator(), invitation.invitation().id()));
            return new ProviderSession(UUID.randomUUID().toString(), email);
        }).when(identityProvider).signIn(eq(email), anyString());
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isGone());
        verify(identityProvider).deleteUser(email);
        verify(identityProvider).terminate(any());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void wrongAttemptsPersistAcrossSessionsAndResendHasCooldown() throws Exception {
        var email = email();
        configure(email);
        doThrow(new RegistrationValidationException(
            HttpStatus.BAD_REQUEST, "code", "Incorrect or expired code"))
            .when(identityProvider).verifyEmail(email, "000000");
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
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
        verify(identityProvider, never()).verifyEmail(email, "123456");
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject=?", email);
        mvc.perform(csrf(postApi("/auth/v1/verification/resend").cookie(resumed))).andExpect(status().isOk());
        mvc.perform(csrf(postApi("/auth/v1/verification").cookie(resumed).content("{\"code\":\"123456\"}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("ADMIN"));
    }

    @Test
    void abandonedUnverifiedIdentityCanBeReplacedButVerifiedIdentityCannot() throws Exception {
        var email = email();
        configure(email);
        when(identityProvider.findByEmail(email)).thenReturn(Optional.of(
            new UserEmail("old-user", email, false)));
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verificationRequired").value(true));
        verify(identityProvider).deleteUser("old-user");
        var protectedEmail = email();
        when(identityProvider.findByEmail(protectedEmail)).thenReturn(Optional.of(
            new UserEmail("verified-user", protectedEmail, true)));
        mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(protectedEmail, null))))
            .andExpect(status().isConflict());
        verify(identityProvider, never()).deleteUser("verified-user");
    }

    @Test
    void replacementCancelsActualPendingDeliveryAndRemovesOldState() {
        var email = email();
        configure(email);
        verification.register(email, "password-12345");
        var oldDelivery = database.queryForObject("SELECT delivery_id FROM pending_registrations WHERE subject=?", UUID.class, email);
        when(identityProvider.findByEmail(email)).thenReturn(Optional.of(new UserEmail(email, email, false)));
        assertThat(catchThrowable(() -> verification.register(email, "password-12345")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(identityProvider, never()).deleteUser(email);
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject=?", email);
        when(identityProvider.createUnverifiedUser(eq(email), anyString())).thenReturn(new PendingUser("new-user", "123456"));
        when(identityProvider.signIn(eq(email), anyString())).thenReturn(new ProviderSession("new-session", "new-user"));
        verification.register(email, "password-12345");
        verify(identityProvider).deleteUser(email);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM pending_registrations WHERE subject=?", Integer.class, email)).isZero();
        assertThat(database.queryForMap("SELECT state,encrypted_payload,payload_nonce FROM email_deliveries WHERE id=?", oldDelivery))
            .containsEntry("state", "FAILED").containsEntry("encrypted_payload", null).containsEntry("payload_nonce", null);
    }

    @Test
    void concurrentVerificationPreventsReplacementFromDeletingCommittedIdentity() throws Exception {
        var email = email();
        configure(email);
        var pending = verification.register(email, "password-12345");
        database.update("UPDATE pending_registrations SET resend_available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject=?", email);
        when(identityProvider.findByEmail(email)).thenReturn(Optional.of(new UserEmail(email, email, false)));
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            verifying.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(identityProvider).verifyEmail(email, "123456");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accepted = executor.submit(() -> verification.verify(pending, "123456"));
            assertThat(verifying.await(5, TimeUnit.SECONDS)).isTrue();
            var replacement = executor.submit(() -> catchThrowable(
                () -> verification.register(email, "password-12345")));
            release.countDown();
            assertThat(accepted.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(replacement.get(5, TimeUnit.SECONDS)).isInstanceOf(RegistrationValidationException.class);
        }
        verify(identityProvider, never()).deleteUser(email);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isOne();
    }

    @Test
    void invitationRegistrationVerifiesOnlyTheIntendedRecipient() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var email = email();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), invitation.token()))))
            .andExpect(status().isBadRequest());
        verify(identityProvider, never()).markEmailVerified(anyString(), anyString());
        configure(email);
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("MEMBER"))
            .andExpect(jsonPath("$.verificationRequired").doesNotExist());
        verify(identityProvider).markEmailVerified(email, email);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isOne();
    }

    private void configure(String email) {
        when(identityProvider.createUser(eq(email), anyString())).thenReturn(email);
        when(identityProvider.createUnverifiedUser(eq(email), anyString())).thenReturn(new PendingUser(email, "123456"));
        var verified = new AtomicBoolean();
        when(identityProvider.userEmail(email)).thenAnswer(call -> new UserEmail(email, email, verified.get()));
        doAnswer(call -> { verified.set(true); return null; }).when(identityProvider).verifyEmail(email, "123456");
        when(identityProvider.resendEmailCode(email)).thenReturn("123456");
        doAnswer(call -> new ProviderSession(UUID.randomUUID().toString(), email))
            .when(identityProvider).signIn(eq(email), anyString());
        doReturn(true).when(identityProvider).isValid(any());
    }
    private Cookie register(String email) throws Exception {
        configure(email);
        var pending = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
        return mvc.perform(csrf(postApi("/auth/v1/verification").cookie(pending).content("{\"code\":\"123456\"}")))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("__Host-session");
    }
    private Cookie login(String email) throws Exception {
        return mvc.perform(csrf(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}")))
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
