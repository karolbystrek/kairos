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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    private UUID locationId;
    private StaffPrincipal principal;

    @BeforeEach
    void createAdministratorFixture() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        principal = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
        var now = Instant.parse("2026-07-24T12:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
                locationId,
                tenantId
        );
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    id, tenant_id, username, tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?)
                """,
                accountId,
                tenantId,
                "order-api-admin-" + accountId,
                now,
                now
        );
    }

    @Test
    void createsAutomaticAndNormalizedCustomOrders() throws Exception {
        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/locations/{locationId}/orders",
                        locationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("1"))
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));

        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/locations/{locationId}/orders",
                        locationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("label", "  Table 4  ")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Table 4"))
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));
    }

    @Test
    void returnsClearProblemDetailsForInvalidCustomLabels() throws Exception {
        var requestBody = objectMapper.writeValueAsString(Map.of("label", "Line 1\u2028Line 2"));

        mockMvc.perform(withAuthenticationAndCsrf(apiPost(
                        "/locations/{locationId}/orders",
                        locationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value("Custom label must be single-line text without control characters"));
    }

    @Test
    void returnsOnlyActiveOrdersFromLocationAndTenantQueues() throws Exception {
        var inPreparation = orderService.createOrder(principal, locationId, null);
        var ready = orderService.createOrder(principal, locationId, null);
        orderService.updateStatus(principal, ready.id(), OrderStatus.READY);
        var completed = orderService.createOrder(principal, locationId, null);
        orderService.updateStatus(principal, completed.id(), OrderStatus.READY);
        orderService.updateStatus(principal, completed.id(), OrderStatus.COMPLETED);
        var canceled = orderService.createOrder(principal, locationId, null);
        orderService.updateStatus(principal, canceled.id(), OrderStatus.CANCELED);

        assertActiveQueue(apiGet("/locations/{locationId}/orders", locationId), inPreparation, ready);
        assertActiveQueue(apiGet("/orders"), inPreparation, ready);
    }

    private void assertActiveQueue(
            MockHttpServletRequestBuilder request,
            StaffOrderView first,
            StaffOrderView second
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
                List.of()
        );
        return request.secure(true).with(authentication(staffAuthentication));
    }

    private Cookie csrfCookie() throws Exception {
        return mockMvc.perform(apiGet("/auth/csrf").secure(true))
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
