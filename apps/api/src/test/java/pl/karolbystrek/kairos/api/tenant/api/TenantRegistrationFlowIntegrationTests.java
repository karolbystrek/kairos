package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantRegistrationFlowIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String ACCESS_COOKIE = "__Host-access-token";
    private static final String REFRESH_COOKIE = "__Host-refresh-token";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String PASSWORD = "Secure-Password-12";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registersOneTenantLocationAndAdministratorWithoutCreatingASession() throws Exception {
        var suffix = UUID.randomUUID().toString();
        var csrf = bootstrapCsrf("192.0.2.101");

        var result = register(
            registrationJson(
                "  ADMIN." + suffix.toUpperCase() + "  ",
                "ADMIN." + suffix + "@EXAMPLE.COM",
                PASSWORD
            ),
            csrf,
            "192.0.2.101"
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("admin." + suffix))
            .andReturn();

        var response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        var tenantId = UUID.fromString(response.get("tenantId").asText());
        var locationId = UUID.fromString(response.get("locationId").asText());
        var administratorId = UUID.fromString(response.get("administratorAccountId").asText());

        assertThat(count("tenants", "id", tenantId)).isOne();
        assertThat(count("locations", "id", locationId)).isOne();
        assertThat(count("locations", "tenant_id", tenantId)).isOne();

        var account = jdbcTemplate.queryForMap(
            """
                SELECT tenant_id, username, email, password_hash, tenant_role, status
                FROM accounts
                WHERE id = ?
                """,
            administratorId
        );
        assertThat(account.get("tenant_id")).isEqualTo(tenantId);
        assertThat(account.get("username")).isEqualTo("admin." + suffix);
        assertThat(account.get("email")).isEqualTo("admin." + suffix + "@example.com");
        assertThat(account.get("tenant_role")).isEqualTo("ADMIN");
        assertThat(account.get("status")).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches(PASSWORD, (String) account.get("password_hash"))).isTrue();
        assertThat(count("location_assignments", "account_id", administratorId)).isZero();
        assertThat(count("sessions", "account_id", administratorId)).isZero();
        assertThat(responseCookies(result, ACCESS_COOKIE)).isEmpty();
        assertThat(responseCookies(result, REFRESH_COOKIE)).isEmpty();

        mockMvc.perform(apiGet("/auth/me").secure(true))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingBlankMalformedOversizedAndDuplicateEmails() throws Exception {
        var suffix = UUID.randomUUID().toString();
        var csrf = bootstrapCsrf("192.0.2.102");
        var invalidEmails = List.of(
            "",
            "not-an-email",
            "a".repeat(243) + "@example.com"
        );

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "administrator": {
                            "username": "missing.email.%s",
                            "password": "%s"
                          }
                        }
                        """.formatted(suffix, PASSWORD)),
                csrf,
                "192.0.2.102"
            ))
            .andExpect(status().isBadRequest());

        for (var index = 0; index < invalidEmails.size(); index++) {
            register(
                registrationJson(
                    "invalid.email." + index + "." + suffix,
                    invalidEmails.get(index),
                    PASSWORD
                ),
                csrf,
                "192.0.2." + (103 + index)
            ).andExpect(status().isBadRequest());
        }

        var duplicateEmail = "duplicate." + suffix + "@example.com";
        register(
            registrationJson(
                "original." + suffix,
                duplicateEmail,
                PASSWORD
            ),
            csrf,
            "192.0.2.106"
        ).andExpect(status().isCreated());

        register(
            registrationJson(
                "different." + suffix,
                duplicateEmail.toUpperCase(),
                PASSWORD
            ),
            csrf,
            "192.0.2.107"
        )
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requiresCsrfAndValidatesTheBcryptPasswordContract() throws Exception {
        var suffix = UUID.randomUUID().toString();

        mockMvc.perform(apiPost("/tenant-registrations")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(
                    "no.csrf." + suffix,
                    "no.csrf." + suffix + "@example.com",
                    PASSWORD
                )))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-missing"));

        var csrf = bootstrapCsrf("192.0.2.108");
        register(
            registrationJson(
                "short.password." + suffix,
                "short." + suffix + "@example.com",
                "short"
            ),
            csrf,
            "192.0.2.108"
        ).andExpect(status().isBadRequest());

        register(
            registrationJson(
                "long.password." + suffix,
                "long." + suffix + "@example.com",
                "ą".repeat(37)
            ),
            csrf,
            "192.0.2.109"
        ).andExpect(status().isBadRequest());
    }

    @Test
    void rollsBackTenantAndLocationWhenAnIdentifierConflicts() throws Exception {
        var suffix = UUID.randomUUID().toString();
        var csrf = bootstrapCsrf("192.0.2.110");
        var username = "rollback." + suffix;

        register(
            registrationJson(
                username,
                "existing." + suffix + "@example.com",
                PASSWORD
            ),
            csrf,
            "192.0.2.110"
        ).andExpect(status().isCreated());

        var tenantCount = countAll("tenants");
        var locationCount = countAll("locations");

        register(
            registrationJson(
                username.toUpperCase(),
                "other." + suffix + "@example.com",
                PASSWORD
            ),
            csrf,
            "192.0.2.111"
        ).andExpect(status().isConflict());

        assertThat(countAll("tenants")).isEqualTo(tenantCount);
        assertThat(countAll("locations")).isEqualTo(locationCount);
    }

    @Test
    void registeredAdministratorCanSignInAndProvisionBothLocationRoles() throws Exception {
        var suffix = UUID.randomUUID().toString();
        var username = "usable.admin." + suffix;
        var csrf = bootstrapCsrf("192.0.2.112");
        var registration = register(
            registrationJson(
                username,
                "usable." + suffix + "@example.com",
                PASSWORD
            ),
            csrf,
            "192.0.2.112"
        )
            .andExpect(status().isCreated())
            .andReturn();
        var locationId = objectMapper.readTree(
            registration.getResponse().getContentAsByteArray()
        ).get("locationId").asText();

        var login = mockMvc.perform(withCsrf(
                apiPost("/auth/login")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, PASSWORD)),
                csrf,
                "192.0.2.112"
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantRole").value("ADMIN"))
            .andExpect(jsonPath("$.assignment").doesNotExist())
            .andReturn();
        var access = activeResponseCookie(login, ACCESS_COOKIE);
        var rotatedCsrf = activeCsrfResponseCookie(login);

        mockMvc.perform(apiGet("/locations")
                .secure(true)
                .cookie(access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(locationId));

        for (var role : List.of("MANAGER", "OPERATOR")) {
            mockMvc.perform(withCsrf(
                    apiPost("/locations/{locationId}/accounts", locationId)
                        .secure(true)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "%s.%s",
                              "email": null,
                              "password": "%s",
                              "role": "%s"
                            }
                            """.formatted(
                                role.toLowerCase(),
                                suffix,
                                PASSWORD,
                                role
                            )),
                    rotatedCsrf,
                    "192.0.2.112"
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.locationId").value(locationId));
        }
    }

    private org.springframework.test.web.servlet.ResultActions register(
        String json,
        Cookie csrf,
        String clientAddress
    ) throws Exception {
        return mockMvc.perform(withCsrf(
            apiPost("/tenant-registrations")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json),
            csrf,
            clientAddress
        ));
    }

    private Cookie bootstrapCsrf(String clientAddress) throws Exception {
        var result = mockMvc.perform(client(
                apiGet("/auth/csrf").secure(true),
                clientAddress
            ))
            .andExpect(status().isOk())
            .andReturn();
        return activeCsrfResponseCookie(result);
    }

    private int count(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
            Integer.class,
            id
        );
    }

    private int countAll(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static MockHttpServletRequestBuilder withCsrf(
        MockHttpServletRequestBuilder request,
        Cookie csrf,
        String clientAddress
    ) {
        return client(request.cookie(csrf).header(CSRF_HEADER, csrf.getValue()), clientAddress);
    }

    private static MockHttpServletRequestBuilder apiGet(String path, Object... uriVariables) {
        return get(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(String path, Object... uriVariables) {
        return post(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder client(
        MockHttpServletRequestBuilder request,
        String clientAddress
    ) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(clientAddress);
            return servletRequest;
        });
    }

    private static String registrationJson(
        String username,
        String email,
        String password
    ) {
        return """
            {
              "administrator": {
                "username": "%s",
                "email": "%s",
                "password": "%s"
              }
            }
            """.formatted(username, email, password);
    }

    private static MockCookie activeResponseCookie(MvcResult result, String name) {
        return responseCookies(result, name).stream()
            .filter(cookie -> cookie.getMaxAge() != 0)
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("No active response cookie named " + name));
    }

    private static Cookie activeCsrfResponseCookie(MvcResult result) {
        return Arrays.stream(result.getResponse().getCookies())
            .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
            .filter(cookie -> cookie.getMaxAge() != 0)
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("No active response cookie named " + CSRF_COOKIE));
    }

    private static List<MockCookie> responseCookies(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .map(MockCookie::parse)
            .filter(cookie -> name.equals(cookie.getName()))
            .toList();
    }
}
