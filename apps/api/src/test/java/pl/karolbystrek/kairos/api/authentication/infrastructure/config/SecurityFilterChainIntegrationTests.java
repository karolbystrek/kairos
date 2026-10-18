package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityFilterChainIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Test
    void servesControllersAndTheActuatorUnderTheConfiguredApiContextPath() throws Exception {
        assertThat(contextPath).isEqualTo(API_CONTEXT_PATH);
        mockMvc.perform(apiGet("/auth/v1/csrf").secure(true)).andExpect(status().isOk());
        // The health status itself depends on which backing services this context reaches; it must be served and reported.
        mockMvc.perform(apiGet("/actuator/health").secure(true)).andExpect(jsonPath("$.status").exists());
    }

    @Test
    void bootstrapsAHostOnlyCsrfCookieAndMatchingTokenAnonymously() throws Exception {
        var result = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CSRF_COOKIE))
                .andExpect(cookie().secure(CSRF_COOKIE, true))
                .andExpect(cookie().httpOnly(CSRF_COOKIE, false))
                .andExpect(cookie().sameSite(CSRF_COOKIE, "Lax"))
                .andExpect(cookie().path(CSRF_COOKIE, "/"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        var csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        var csrfResponse = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        assertThat(csrfCookie.getDomain()).isNull();
        assertThat(csrfResponse.get("token").asText()).isEqualTo(csrfCookie.getValue());
    }

    @Test
    void returnsProblemDetailsWhenAuthenticationIsMissing() throws Exception {
        mockMvc.perform(apiGet("/locations/v1").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:kairos:problem:unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void distinguishesAMissingCsrfToken() throws Exception {
        mockMvc.perform(apiPost("/auth/v1/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"panel@example.com","password":"not-a-real-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-missing"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void distinguishesAnInvalidCsrfToken() throws Exception {
        var bootstrap = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(apiPost("/auth/v1/login")
                        .secure(true)
                        .cookie(bootstrap.getResponse().getCookie(CSRF_COOKIE))
                        .header(CSRF_HEADER, "not-the-cookie-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"panel@example.com","password":"not-a-real-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-invalid"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rejectsAValidCsrfTokenSubmittedAsAFormParameterInsteadOfTheRequiredHeader() throws Exception {
        var bootstrap = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
                .andExpect(status().isOk())
                .andReturn();
        var csrf = bootstrap.getResponse().getCookie(CSRF_COOKIE);

        mockMvc.perform(apiPost("/auth/v1/login")
                        .secure(true)
                        .cookie(csrf)
                        .param("_csrf", xorEncode(csrf.getValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"panel@example.com","password":"not-a-real-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-invalid"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void exposesNotificationConfigurationAnonymously() throws Exception {
        mockMvc.perform(apiGet("/customer-notifications/v1/configuration").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationServerKey").isNotEmpty());
    }

    @Test
    void keepsAnonymousNotificationMutationsCsrfProtected() throws Exception {
        mockMvc.perform(apiPut("/customer-notifications/v1/subscription")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(
                        "urn:kairos:problem:csrf-token-missing"
                ));
        var bootstrap = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
                .andExpect(status().isOk())
                .andReturn();
        var csrf = bootstrap.getResponse().getCookie(CSRF_COOKIE);

        mockMvc.perform(apiPut("/customer-notifications/v1/subscription")
                        .secure(true)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "INVALID_CUSTOMER_PUSH_SUBSCRIPTION"
                ));
    }

    @Test
    void protectsUsageWritesAndBoundsAnonymousReviewPayloadsWithoutContentLength() throws Exception {
        var reference = UUID.randomUUID();
        var reviewPath = "/tracked-orders/v1/" + reference + "/review-actions";
        mockMvc.perform(apiPost(reviewPath).secure(true).contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"review_link_clicked\"}"))
                .andExpect(status().isForbidden());
        var bootstrap = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true)).andReturn();
        var csrf = bootstrap.getResponse().getCookie(CSRF_COOKIE);
        mockMvc.perform(apiPost("/usage-analytics/v1/actions").secure(true).cookie(csrf)
                .header(CSRF_HEADER, csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(apiPost(reviewPath).secure(true).cookie(csrf).header(CSRF_HEADER, csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(" ".repeat(8193))
                .with(request -> { request.removeHeader("Content-Length"); request.addHeader("Transfer-Encoding", "chunked"); return request; }))
                .andExpect(status().isPayloadTooLarge());
        // An unknown reference never uses rate-limit budget, so made-up references cannot fill the limiter.
        for (var request = 0; request < 61; request++) {
            mockMvc.perform(apiPost(reviewPath).secure(true).cookie(csrf).header(CSRF_HEADER, csrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"review_link_clicked\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void grantsNoCrossOriginBrowserAccessBecauseFrontendsCallTheirOwnOrigin() throws Exception {
        for (var origin : new String[]{"http://localhost:3000", "http://localhost:3001", "https://unknown.example.org"}) {
            assertCorsRejected(origin, "/auth/v1/csrf", "GET");
            assertCorsRejected(origin, "/tracked-orders/v1/reference", "GET");
            assertCorsRejected(origin, "/orders/v1", "POST");
            assertCorsRejected(origin, "/external/orders/v1", "GET");
        }
    }

    @Test
    void permitsInternalAsyncDispatchesWithoutReauthorizingTheOriginalRequest() throws Exception {
        mockMvc.perform(apiGet("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ASYNC);
                    return request;
                }))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotIn(401, 403));
    }

    private static MockHttpServletRequestBuilder apiGet(String path) {
        return get(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPut(String path) {
        return put(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private void assertCorsRejected(String origin, String path, String method) throws Exception {
        mockMvc.perform(corsPreflight(origin, path, method))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Origin")
                ).isNull());
    }

    private static MockHttpServletRequestBuilder corsPreflight(
            String origin,
            String path,
            String method
    ) {
        return options(API_CONTEXT_PATH + path)
                .contextPath(API_CONTEXT_PATH)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method);
    }

    private static String xorEncode(String token) {
        var tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        var encoded = new byte[tokenBytes.length * 2];
        System.arraycopy(tokenBytes, 0, encoded, tokenBytes.length, tokenBytes.length);
        return Base64.getUrlEncoder().encodeToString(encoded);
    }
}
