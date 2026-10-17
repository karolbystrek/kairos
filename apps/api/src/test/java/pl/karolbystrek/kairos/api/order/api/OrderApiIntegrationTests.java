package pl.karolbystrek.kairos.api.order.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    private UUID locationId;
    private StaffPrincipal principal;
    private String providerSubject;

    @BeforeEach
    void createAdministratorFixture() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        principal = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
        providerSubject = UUID.randomUUID().toString();
        var now = Instant.parse("2026-07-24T12:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
                locationId,
                tenantId,
                "Test location",
                "test location"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            accountId,
            providerSubject,
            tenantId,
            "order-api-admin-" + accountId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Test
    void createsAutomaticAndNormalizedCustomOrders() throws Exception {
        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/orders/v1"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationId",
                                locationId
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.label").doesNotExist())
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));

        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/orders/v1"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationId",
                                locationId,
                                "label",
                                "  Table 4  "
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.label").value("Table 4"))
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));
    }

    @Test
    void returnsClearProblemDetailsForInvalidCustomLabels() throws Exception {
        var requestBody = objectMapper.writeValueAsString(Map.of(
                "locationId",
                locationId,
                "label",
                "Line 1\u2028Line 2"
        ));

        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/orders/v1"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());

        mockMvc.perform(withAuthenticationAndCsrf(apiPost("/orders/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The request is invalid"));
    }

    @Test
    void returnsOnlyActiveOrdersFromLocationAndTenantQueues() throws Exception {
        var inPreparation = orderService.createOrder(principal, locationId, null, List.of());
        var ready = orderService.createOrder(principal, locationId, null, List.of());
        orderService.updateStatus(principal, ready.id(), OrderStatus.READY);
        var completed = orderService.createOrder(principal, locationId, null, List.of());
        orderService.updateStatus(principal, completed.id(), OrderStatus.READY);
        orderService.updateStatus(principal, completed.id(), OrderStatus.COMPLETED);
        var canceled = orderService.createOrder(principal, locationId, null, List.of());
        orderService.updateStatus(principal, canceled.id(), OrderStatus.CANCELED);

        assertActiveQueue(
                apiGet("/orders/v1").queryParam("locationId", locationId.toString()),
                inPreparation,
                ready
        );
        assertActiveQueue(apiGet("/orders/v1"), inPreparation, ready);
    }

    @Test
    void queueStreamUsesRestAuthorizationAndCarriesOnlyTheActiveQueue() throws Exception {
        var active = orderService.createOrder(principal, locationId, "active-stream-order", List.of());
        var terminal = orderService.createOrder(principal, locationId, "terminal-stream-order", List.of());
        orderService.updateStatus(principal, terminal.id(), OrderStatus.CANCELED);

        mockMvc.perform(apiGet("/orders/v1/events"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withSignedInSession(withAuthentication(apiGet("/orders/v1/events")
                        .queryParam("locationId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        var stream = mockMvc.perform(withSignedInSession(withAuthentication(apiGet("/orders/v1/events")
                        .queryParam("locationId", locationId.toString()))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse();
        // Wait for the payload, not the event name: SSE writes them separately.
        assertThat(awaitStreamContent(stream, active.id().toString()))
                .contains("event:order-queue")
                .doesNotContain(terminal.id().toString());
    }

    private void assertActiveQueue(
            MockHttpServletRequestBuilder request,
            OrderView first,
            OrderView second
    ) throws Exception {
        mockMvc.perform(withAuthentication(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        first.id().toString(),
                        second.id().toString()
                )))
                .andExpect(jsonPath("$[*].status").value(containsInAnyOrder(
                        "IN_PREPARATION",
                        "READY"
                )));
    }

    // The stream re-checks the session cutoff, so it needs the sign-in the staff session filter would have recorded.
    private MockHttpServletRequestBuilder withSignedInSession(MockHttpServletRequestBuilder request) {
        return request
                .requestAttr(AuthenticationSessionService.IDENTITY, providerSubject)
                .requestAttr(AuthenticationSessionService.SIGNED_IN_AT, Instant.parse("2026-07-24T12:00:00Z"));
    }

    private MockHttpServletRequestBuilder withAuthenticationAndCsrf(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        var csrf = csrfCookie();
        return withAuthentication(request)
                .cookie(csrf)
                .header(CSRF_HEADER, csrf.getValue());
    }

    private MockHttpServletRequestBuilder withAuthentication(
            MockHttpServletRequestBuilder request
    ) {
        var staffAuthentication = new UsernamePasswordAuthenticationToken(
                principal,
                "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"))
        );
        return request.secure(true).with(authentication(staffAuthentication));
    }

    private Cookie csrfCookie() throws Exception {
        return mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(CSRF_COOKIE);
    }

    private static MockHttpServletRequestBuilder apiGet(String path, Object... uriVariables) {
        return get(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(String path, Object... uriVariables) {
        return post(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }
}
