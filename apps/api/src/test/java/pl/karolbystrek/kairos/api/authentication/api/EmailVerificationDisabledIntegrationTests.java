package pl.karolbystrek.kairos.api.authentication.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.PendingUser;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.ProviderSession;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient.UserEmail;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "kairos.authentication.email-verification-enabled=false")
@AutoConfigureMockMvc
class EmailVerificationDisabledIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired private MockMvc mvc;
    private final JdbcTemplate database = PostgresTestDatabase.ownerDatabase();

    @Test
    void registrationImmediatelyCreatesAdministratorAndSessionWithoutEmail() throws Exception {
        var email = configure();
        completes(postApi("/tenant-registrations/v1").content("{\"email\":\"" + email
            + "\",\"password\":\"password-12345\",\"passwordConfirmation\":\"password-12345\"}"), email);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM email_deliveries WHERE owner_identity=?", Integer.class, email)).isZero();
    }

    @Test
    void unverifiedSignInCompletesOnboardingAndCancelsExistingCode() throws Exception {
        var email = configure();
        var delivery = UUID.randomUUID();
        database.update("INSERT INTO email_deliveries(id,owner_scope,owner_identity,encrypted_payload,payload_nonce,created_at,next_attempt_at) VALUES (?,'verification',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            delivery, email, new byte[] {1}, new byte[12]);
        database.update("INSERT INTO pending_registrations(subject,email,resend_available_at,delivery_id) VALUES (?,?,CURRENT_TIMESTAMP,?)", email, email, delivery);
        completes(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}"), email);
        assertThat(database.queryForObject("SELECT state FROM email_deliveries WHERE id=?", String.class, delivery)).isEqualTo("FAILED");
        verify(identityProvider, never()).resendEmailCode(anyString());
    }

    @Test
    void unverifiedSignInWithoutPendingCodeCompletesWithoutSendingEmail() throws Exception {
        var email = configure();
        completes(postApi("/auth/v1/login").content("{\"email\":\"" + email + "\",\"password\":\"password-12345\"}"), email);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM email_deliveries WHERE owner_identity=?", Integer.class, email)).isZero();
        verify(identityProvider, never()).resendEmailCode(anyString());
    }

    private String configure() {
        var email = UUID.randomUUID() + "@example.com";
        when(identityProvider.createUnverifiedUser(eq(email), anyString())).thenReturn(new PendingUser(email, "123456"));
        when(identityProvider.signIn(eq(email), anyString())).thenReturn(new ProviderSession(UUID.randomUUID().toString(), email));
        when(identityProvider.isValid(any())).thenReturn(true);
        when(identityProvider.userEmail(email)).thenReturn(new UserEmail(email, email, false));
        return email;
    }

    private void completes(MockHttpServletRequestBuilder request, String email) throws Exception {
        var bootstrap = mvc.perform(get("/api/auth/v1/csrf").contextPath("/api").secure(true)).andReturn();
        var csrf = bootstrap.getResponse().getCookie("__Host-XSRF-TOKEN");
        var result = mvc.perform(request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantRole").value("ADMIN"))
            .andExpect(jsonPath("$.verificationRequired").doesNotExist()).andReturn();
        mvc.perform(get("/api/auth/v1/me").contextPath("/api").secure(true)
            .cookie(result.getResponse().getCookie("__Host-session"))).andExpect(status().isOk());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email)).isOne();
        assertThat(database.queryForObject("SELECT COUNT(*) FROM pending_registrations WHERE subject=?", Integer.class, email)).isZero();
        verify(identityProvider).markEmailVerified(email, email);
        verify(identityProvider, never()).verifyEmail(anyString(), anyString());
    }

    private static MockHttpServletRequestBuilder postApi(String path) {
        return post("/api" + path).contextPath("/api").secure(true).contentType(MediaType.APPLICATION_JSON);
    }
}
