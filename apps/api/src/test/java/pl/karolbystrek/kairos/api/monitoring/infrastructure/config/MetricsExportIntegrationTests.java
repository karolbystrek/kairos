package pl.karolbystrek.kairos.api.monitoring.infrastructure.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.metrics.enable.http.client.requests=false"
})
@AutoConfigureMetrics(export = true)
class MetricsExportIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGH";
    private static Path secret;

    @Autowired
    private MockMvc mvc;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void metricsCredential(DynamicPropertyRegistry properties) throws Exception {
        secret = Files.createTempFile("metrics-export-test-", ".token");
        Files.writeString(secret, TOKEN);
        properties.add("kairos.monitoring.token-location", () -> secret.toUri().toString());
    }

    @AfterAll
    static void removeSecret() throws Exception {
        Files.deleteIfExists(secret);
    }

    @Test
    void onlyOperatorBearerCredentialCanScrapeAndNeverCreatesASession() throws Exception {
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api")
                        .header("Authorization", "Bearer integration-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api").queryParam("access_token", TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api").header("Authorization", "Basic " + TOKEN))
                .andExpect(status().isUnauthorized());
        var session = new MockHttpSession();
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "staff", null, AuthorityUtils.createAuthorityList("ROLE_TENANT_ACCOUNT", "METRICS_EXPORT")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api").session(session))
                .andExpect(status().isUnauthorized());
        var result = mvc.perform(get("/api/actuator/prometheus").contextPath("/api")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("jvm_memory");
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(post("/api/actuator/prometheus").contextPath("/api")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/actuator/env").contextPath("/api").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
        var health = mvc.perform(get("/api/actuator/health").contextPath("/api")).andReturn();
        assertThat(health.getResponse().getStatus()).isIn(200, 503);
    }
    @Test
    void realServerExportsOnlyRouteTemplatesAndNoPrivateRequestValues() throws Exception {
        var privateReference = "b5274890-d72d-4ea7-b857-9563dacebada";
        try (var client = HttpClient.newHttpClient()) {
            var trackedOrder = HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/api/tracked-orders/v1/" + privateReference + "?private=secret-query"))
                    .build();
            assertThat(client.send(trackedOrder, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            var scrape = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/actuator/prometheus"))
                    .header("Authorization", "Bearer " + TOKEN).build();
            var response = client.send(scrape, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("http_server_requests", "/tracked-orders/v1/{trackingReference}")
                    .doesNotContain(privateReference, "secret-query", TOKEN, "http_client_requests");
        }
    }

}
