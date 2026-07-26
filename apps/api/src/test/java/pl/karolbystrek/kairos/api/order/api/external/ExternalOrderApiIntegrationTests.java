package pl.karolbystrek.kairos.api.order.api.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyManagementService;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExternalOrderApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private ApiKeyManagementService apiKeyService;

    private IntegrationTestFixture.TenantFixture tenant;
    private String secret;

    @BeforeEach
    void createFixture() {
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var integration = integrationService.create(
                tenant.administrator(),
                "External API"
        );
        secret = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "External writer",
                Set.of("orders:write"),
                Set.of(tenant.firstLocationId()),
                null
        ).secret();
    }

    @Test
    void acceptsOnlyAuthorizationHeaderCredentials() throws Exception {
        mockMvc.perform(apiGet("/external/orders/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));

        mockMvc.perform(apiGet("/external/orders/v1")
                        .queryParam("access_token", secret))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(authenticated(apiGet("/external/orders/v1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void exposesIdempotentCreationAndDesiredStateCommands() throws Exception {
        var creationBody = objectMapper.writeValueAsString(Map.of(
                "locationId",
                tenant.firstLocationId()
        ));
        var creation = authenticated(apiPost("/external/orders/v1"))
                .header("Idempotency-Key", " api-order ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(creationBody);
        var response = mockMvc.perform(creation)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"))
                .andReturn();
        var orderId = objectMapper.readTree(response.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(authenticated(apiPost("/external/orders/v1"))
                        .header("Idempotency-Key", " api-order ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        var unchangedBody = objectMapper.writeValueAsString(Map.of(
                "status",
                "IN_PREPARATION"
        ));
        mockMvc.perform(authenticated(apiPut(
                        "/external/orders/v1/{orderId}/status",
                        orderId
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unchangedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));

        mockMvc.perform(authenticated(apiPut(
                        "/external/orders/v1/{orderId}/status",
                        orderId
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status",
                                "READY"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(authenticated(apiPut(
                        "/external/orders/v1/{orderId}/status",
                        orderId
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unchangedBody))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsExplicitlyUnassignedLocations() throws Exception {
        mockMvc.perform(authenticated(apiPost("/external/orders/v1"))
                        .header("Idempotency-Key", "unassigned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationId",
                                tenant.secondLocationId()
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(authenticated(apiGet("/external/orders/v1")
                        .queryParam(
                                "locationId",
                                tenant.secondLocationId().toString()
                        )))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request
    ) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
    }

    private static MockHttpServletRequestBuilder apiGet(
            String path,
            Object... uriVariables
    ) {
        return get(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(
            String path,
            Object... uriVariables
    ) {
        return post(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPut(
            String path,
            Object... uriVariables
    ) {
        return put(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }
}
