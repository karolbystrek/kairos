package pl.karolbystrek.kairos.api.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackedOrderApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsTheAnonymousCustomerRepresentationIncludingTerminalOrders() throws Exception {
        var trackingReference = insertOrder("42", "COMPLETED");

        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}", trackingReference))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.label").value("42"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsNotFoundForUnknownAnonymousTrackingAndEventRequests() throws Exception {
        var trackingReference = UUID.randomUUID();

        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}", trackingReference))
                .andExpect(status().isNotFound());
        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}/events", trackingReference))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesAnAnonymousReadOnlyEventStreamOnlyForActiveOrders() throws Exception {
        var activeReference = insertOrder("7", "READY");
        var terminalReference = insertOrder("8", "CANCELED");

        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}/events", activeReference))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}/events", terminalReference))
                .andExpect(status().isNoContent());
    }

    private UUID insertOrder(String label, String status) {
        var tenantId = UUID.randomUUID();
        var locationId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var trackingReference = UUID.randomUUID();
        var now = Instant.parse("2026-07-24T12:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
                "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
                locationId,
                tenantId
        );
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    id, location_id, tracking_reference, label, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                locationId,
                trackingReference,
                label,
                status,
                now,
                now
        );
        return trackingReference;
    }

    private static MockHttpServletRequestBuilder apiGet(String path, Object... uriVariables) {
        return get(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }
}
