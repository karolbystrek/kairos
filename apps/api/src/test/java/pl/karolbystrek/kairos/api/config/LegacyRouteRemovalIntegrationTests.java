package pl.karolbystrek.kairos.api.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LegacyRouteRemovalIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesNoAliasesForTheReplacedBrowserRouteFamilies() throws Exception {
        var locationId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var trackingReference = UUID.randomUUID();
        var csrf = csrfCookie();

        assertNotFound(get(API_CONTEXT_PATH + "/auth/csrf"), csrf);
        assertNotFound(get(API_CONTEXT_PATH + "/locations"), csrf);
        assertNotFound(get(API_CONTEXT_PATH + "/orders"), csrf);
        assertNotFound(get(
                API_CONTEXT_PATH + "/tracked-orders/{trackingReference}",
                trackingReference
        ), csrf);
        assertNotFound(post(
                API_CONTEXT_PATH + "/locations/{locationId}/orders",
                locationId
        ), csrf);
        assertNotFound(patch(
                API_CONTEXT_PATH + "/orders/{orderId}/status",
                orderId
        ), csrf);
        assertNotFound(post(
                API_CONTEXT_PATH + "/locations/{locationId}/accounts",
                locationId
        ), csrf);
        assertNotFound(patch(
                API_CONTEXT_PATH + "/accounts/{accountId}/status",
                accountId
        ), csrf);
        assertNotFound(post(API_CONTEXT_PATH + "/tenant-registrations"), csrf);
    }

    private void assertNotFound(
            MockHttpServletRequestBuilder request,
            Cookie csrf
    ) throws Exception {
        var principal = new StaffPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TenantRole.ADMIN
        );
        var staffAuthentication = new UsernamePasswordAuthenticationToken(
                principal,
                "credentials",
                List.of()
        );
        mockMvc.perform(request
                        .contextPath(API_CONTEXT_PATH)
                        .with(authentication(staffAuthentication))
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNotFound());
    }

    private Cookie csrfCookie() throws Exception {
        return mockMvc.perform(get(API_CONTEXT_PATH + "/auth/v1/csrf")
                        .contextPath(API_CONTEXT_PATH)
                        .secure(true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(CSRF_COOKIE);
    }
}
