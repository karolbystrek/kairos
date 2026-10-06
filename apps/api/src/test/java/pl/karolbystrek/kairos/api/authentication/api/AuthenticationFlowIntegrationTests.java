package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.Cookie;
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
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate database;
    @Autowired private AccountInvitationService invitations;

    @Test
    void registrationImmediatelyCreatesTenantAndDurableSecureSessionWithoutVerification() throws Exception {
        var email = email();
        configure(email);
        var result = mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("ADMIN"))
            .andExpect(jsonPath("$.username").doesNotExist())
            .andExpect(jsonPath("$.verificationRequired").doesNotExist())
            .andExpect(cookie().httpOnly("__Host-session", true)).andExpect(cookie().secure("__Host-session", true))
            .andExpect(cookie().sameSite("__Host-session", "Lax")).andExpect(cookie().path("__Host-session", "/"))
            .andExpect(cookie().maxAge("__Host-session", 30 * 86400)).andReturn();
        var cookie = result.getResponse().getCookie("__Host-session");
        assertThat(cookie.getDomain()).isNull();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isOne();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = (SELECT CAST(id AS VARCHAR) FROM accounts WHERE email = ?)", Integer.class, email)).isOne();
        assertThat(database.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE principal_name = (SELECT CAST(id AS VARCHAR) FROM accounts WHERE email = ?)", Integer.class, email)).isEqualTo(30 * 86400);
        mvc.perform(getApi("/auth/v1/me").cookie(cookie)).andExpect(status().isOk())
            .andExpect(cookie().value("__Host-session", cookie.getValue()))
            .andExpect(cookie().maxAge("__Host-session", 30 * 86400));
        verify(identityProvider).isValid(any());
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
    void invitationIsConsumedImmediatelyAtItsFixedLocationAndRole() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.MANAGER);
        var email = email();
        configure(email);
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assignment.locationId").value(tenant.firstLocationId().toString()))
            .andExpect(jsonPath("$.assignment.role").value("MANAGER"));
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), invitation.token()))))
            .andExpect(status().isGone());
    }

    @Test
    void invalidInvitationIsRejectedBeforeProviderCreationAndSignedInRegistrationIsRejected() throws Exception {
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email(), "invalid"))))
            .andExpect(status().isNotFound());
        verify(identityProvider, never()).createUser(anyString(), anyString());
        var session = register(email());
        mvc.perform(csrf(postApi("/tenant-registrations/v1").cookie(session).content(registration(email(), null))))
            .andExpect(status().isForbidden());
    }

    @Test
    void providerIdentityIsCleanedUpWhenInvitationBecomesUnavailableBeforeCommit() throws Exception {
        var tenant = new IntegrationTestFixture(database).createTenant();
        var invitation = invitations.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.MANAGER);
        var email = email();
        configure(email);
        doAnswer(call -> {
            invitations.revoke(tenant.administrator(), invitation.invitation().id());
            return new ProviderSession(UUID.randomUUID().toString(), email);
        }).when(identityProvider).signIn(eq(email), anyString());
        mvc.perform(csrf(postApi("/account-invitation-redemptions/v1").content(registration(email, invitation.token()))))
            .andExpect(status().isGone());
        verify(identityProvider).deleteUser(email);
        verify(identityProvider).terminate(any());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email)).isZero();
    }

    private void configure(String email) {
        when(identityProvider.createUser(eq(email), anyString())).thenReturn(email);
        doAnswer(call -> new ProviderSession(UUID.randomUUID().toString(), email))
            .when(identityProvider).signIn(eq(email), anyString());
        doReturn(true).when(identityProvider).isValid(any());
    }
    private Cookie register(String email) throws Exception {
        configure(email);
        return mvc.perform(csrf(postApi("/tenant-registrations/v1").content(registration(email, null))))
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
