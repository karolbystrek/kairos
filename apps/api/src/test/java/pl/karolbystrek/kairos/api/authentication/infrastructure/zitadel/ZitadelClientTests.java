package pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ZitadelClientTests {
    private MockRestServiceServer server;
    private ZitadelClient client;
    private final ZitadelClient.ProviderSession session = new ZitadelClient.ProviderSession("session", "user");

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://zitadel:8080").defaultHeader("Authorization", "Bearer service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ZitadelClient(builder.build(), Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void springConstructsTheRealClientFromConfiguredBackendCredentials() {
        new ApplicationContextRunner()
            .withBean(AuthenticationProperties.class,
                () -> new AuthenticationProperties(
                    "http://zitadel:8080", new ClassPathResource("keys/test-provider-token.txt")))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(ZitadelClient.class)
            .run(context -> assertThat(context).hasSingleBean(ZitadelClient.class));
    }

    @Test
    void loginChecksUserAndPasswordThenValidatesFactorsWithoutPuttingTokensInUrls() {
        server.expect(requestTo("http://zitadel:8080/v2/sessions")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"checks\":{\"user\":{\"loginName\":\"email@example.com\"},\"password\":{\"password\":\"password\"}}}"))
            .andRespond(withSuccess("{\"sessionId\":\"session\",\"sessionToken\":\"secret\"}", MediaType.APPLICATION_JSON));
        state("{\"factors\":{\"user\":{\"id\":\"user\"},\"password\":{\"verifiedAt\":\"2026-10-06T11:00:00Z\"}}}");
        assertThat(client.signIn("email@example.com", "password")).isEqualTo(session);
        assertThat(session.toString()).doesNotContain("secret");
        server.verify();
    }

    @Test
    void missingPasswordFactorAfterPasswordChangeAndChangedIdentityAreNotAuthenticated() {
        state("{\"factors\":{\"user\":{\"id\":\"user\"}}}");
        state("{\"factors\":{\"user\":{\"id\":\"other\"},\"password\":{\"verifiedAt\":\"2026-10-06T11:00:00Z\"}}}");
        assertThat(client.isValid(session)).isFalse();
        assertThat(client.isValid(session)).isFalse();
        server.verify();
    }

    @Test
    void expiredAndTerminatedProviderSessionsAreRejected() {
        state("{\"expirationDate\":\"2026-10-05T12:00:00Z\",\"factors\":{\"user\":{\"id\":\"user\"},\"password\":{\"verifiedAt\":\"2026-10-04T11:00:00Z\"}}}");
        server.expect(requestTo("http://zitadel:8080/v2/sessions/session")).andRespond(withResourceNotFound());
        assertThat(client.isValid(session)).isFalse();
        assertThat(client.isValid(session)).isFalse();
        server.verify();
    }

    @Test
    void providerOutageIsTemporaryAndDoesNotBecomeInvalidCredentials() {
        server.expect(requestTo("http://zitadel:8080/v2/sessions/session")).andRespond(withServerError());
        assertThatThrownBy(() -> client.isValid(session)).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void registrationPreservesUnverifiedEmailAndDoesNotSendMail() {
        server.expect(requestTo("http://zitadel:8080/management/v1/orgs/me"))
            .andRespond(withSuccess("{\"org\":{\"id\":\"org\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://zitadel:8080/v2/users/new")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.human.email.returnCode").isMap())
            .andExpect(jsonPath("$.human.email.isVerified").doesNotExist())
            .andExpect(jsonPath("$.human.password.password").value("password"))
            .andExpect(jsonPath("$.organizationId").value("org"))
            .andRespond(withSuccess("{\"id\":\"user\",\"emailCode\":\"discard-me\"}", MediaType.APPLICATION_JSON));
        assertThat(client.createUser("email@example.com", "password")).isEqualTo("user");
        server.verify();
    }

    @Test
    void registrationTranslatesKnownProviderPasswordFailuresWithoutExposingProviderDetails() {
        for (var failure : new String[][] {
            { "DOMAIN-HuJf6", "Password is too short." },
            { "DOMAIN-co3Xw", "Include a lowercase letter." },
            { "DOMAIN-VoaRj", "Include an uppercase letter." },
            { "DOMAIN-ZBv4H", "Include a number." },
            { "DOMAIN-ZDLwA", "Include a symbol." }
        }) {
            server.reset();
            server.expect(requestTo("http://zitadel:8080/management/v1/orgs/me"))
                .andRespond(withSuccess("{\"org\":{\"id\":\"org\"}}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("http://zitadel:8080/v2/users/new"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"private provider details (" + failure[0] + ")\"}"));
            assertThatThrownBy(() -> client.createUser("email@example.com", "password"))
                .isInstanceOfSatisfying(RegistrationValidationException.class, error -> {
                    assertThat(error.getField()).isEqualTo("password");
                    assertThat(error.getReason()).isEqualTo(failure[1]);
                });
            server.verify();
        }
    }

    @Test
    void passwordChangeUsesCurrentPasswordAndProviderPolicy() {
        server.expect(requestTo("http://zitadel:8080/v2/users/user")).andExpect(method(HttpMethod.PATCH))
            .andExpect(jsonPath("$.human.password.currentPassword").value("current"))
            .andExpect(jsonPath("$.human.password.password.password").value("replacement"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        client.changePassword("user", "current", "replacement");
        server.verify();
    }

    private void state(String session) {
        server.expect(requestTo("http://zitadel:8080/v2/sessions/session"))
            .andRespond(withSuccess("{\"session\":" + session + "}", MediaType.APPLICATION_JSON));
    }
}
