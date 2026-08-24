package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.AccessTokenIssuer;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantRegistrationInvitationApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String ACCESS_COOKIE = "__Host-access-token";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    void platformOperatorCreatesAndListsAHashedOneTimeTenantInvitation() throws Exception {
        var operator = insertPlatformOperator("issuer");
        var access = accessCookie(operator.accountId());
        var csrf = bootstrapCsrf();

        var creation = mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitations/v1")
                    .secure(true)
                    .cookie(access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"label":"  Warsaw launch  "}
                        """),
                csrf
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.label").value("Warsaw launch"))
            .andExpect(jsonPath("$.issuedByUsername").value(operator.username()))
            .andExpect(jsonPath("$.invitationLink").isString())
            .andReturn();

        var body = objectMapper.readTree(creation.getResponse().getContentAsByteArray());
        var invitationId = UUID.fromString(body.get("id").asText());
        var createdAt = Instant.parse(body.get("createdAt").asText());
        var expiresAt = Instant.parse(body.get("expiresAt").asText());
        var link = body.get("invitationLink").asText();
        assertThat(expiresAt).isEqualTo(createdAt.plus(Duration.ofDays(7)));
        assertThat(link).startsWith("http://localhost:3001/tenant-registration#invitation=");
        var token = link.substring(link.indexOf('=') + 1);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT token_hash FROM tenant_registration_invitations WHERE id = ?",
            String.class,
            invitationId
        )).isNotEqualTo(token).matches("[0-9a-f]{64}");

        mockMvc.perform(apiGet("/tenant-registration-invitations/v1")
                .secure(true)
                .cookie(access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(invitationId.toString()))
            .andExpect(jsonPath("$[0].label").value("Warsaw launch"))
            .andExpect(jsonPath("$[0].invitationLink").doesNotExist());
    }

    @Test
    void validInvitationPreviewsAndAtomicallyRegistersTheFirstAdministratorSession() throws Exception {
        var operator = insertPlatformOperator("registration");
        var csrf = bootstrapCsrf();
        var creation = mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitations/v1")
                    .secure(true)
                    .cookie(accessCookie(operator.accountId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"label":"Private onboarding"}
                        """),
                csrf
            ))
            .andExpect(status().isCreated())
            .andReturn();
        var link = objectMapper.readTree(creation.getResponse().getContentAsByteArray())
            .get("invitationLink")
            .asText();
        var token = link.substring(link.indexOf('=') + 1);

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitation-previews/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                        """.formatted(token)),
                csrf
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").isString())
            .andExpect(jsonPath("$.label").doesNotExist())
            .andExpect(jsonPath("$.issuedByUsername").doesNotExist());

        var suffix = UUID.randomUUID().toString();
        var registration = mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "token":"%s",
                          "username":"  Admin.%s  ",
                          "email":"ADMIN.%s@EXAMPLE.COM",
                          "password":"Correct-Horse-12",
                          "passwordConfirmation":"Correct-Horse-12"
                        }
                        """.formatted(token, suffix, suffix)),
                csrf
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("TENANT_ACCOUNT"))
            .andExpect(jsonPath("$.username").value("admin." + suffix))
            .andExpect(jsonPath("$.tenantRole").value("ADMIN"))
            .andExpect(jsonPath("$.assignment").doesNotExist())
            .andExpect(cookie().exists("__Host-access-token"))
            .andExpect(cookie().exists("__Host-refresh-token"))
            .andReturn();

        var body = objectMapper.readTree(registration.getResponse().getContentAsByteArray());
        var tenantId = UUID.fromString(body.get("tenantId").asText());
        var accountId = UUID.fromString(body.get("accountId").asText());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locations WHERE tenant_id = ?",
            Integer.class,
            tenantId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT kind FROM accounts WHERE id = ?",
            String.class,
            accountId
        )).isEqualTo("TENANT_ACCOUNT");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM tenant_registration_invitations WHERE redeemed_account_id = ?",
            String.class,
            accountId
        )).isEqualTo("REDEEMED");

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitation-previews/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                        """.formatted(token)),
                csrf
            ))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:tenant-registration-invitation-redeemed"
            ));
    }

    @Test
    void validationConflictAndSignedInAttemptsDoNotConsumeAnInvitation() throws Exception {
        var operator = insertPlatformOperator("non-consuming");
        var access = accessCookie(operator.accountId());
        var csrf = bootstrapCsrf();
        var creation = createInvitation(operator.accountId(), csrf, "Correctable attempt");
        var token = invitationToken(creation);

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(
                        token,
                        "new-administrator",
                        "new-administrator@example.com",
                        "does-not-match"
                    )),
                csrf
            ))
            .andExpect(status().isBadRequest());
        assertInvitationPreviews(token, csrf);

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(
                        token,
                        operator.username(),
                        "available@example.com",
                        "Correct-Horse-12"
                    )),
                csrf
            ))
            .andExpect(status().isConflict());
        assertInvitationPreviews(token, csrf);

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations/v1")
                    .secure(true)
                    .cookie(access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(
                        token,
                        "signed-in-attempt",
                        "signed-in-attempt@example.com",
                        "Correct-Horse-12"
                    )),
                csrf
            ))
            .andExpect(status().isForbidden());
        assertInvitationPreviews(token, csrf);

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registrations/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username":"missing-token",
                          "email":"missing-token@example.com",
                          "password":"Correct-Horse-12",
                          "passwordConfirmation":"Correct-Horse-12"
                        }
                        """),
                csrf
            ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:tenant-registration-invitation-invalid"
            ));
    }

    @Test
    void everyEnabledOperatorCanListAndRevokePendingInvitations() throws Exception {
        var issuer = insertPlatformOperator("issuer-one");
        var reviewer = insertPlatformOperator("reviewer-two");
        var csrf = bootstrapCsrf();
        var creation = createInvitation(issuer.accountId(), csrf, "Cross-operator review");
        var invitationId = UUID.fromString(creation.get("id").asText());
        var token = invitationToken(creation);

        mockMvc.perform(apiGet("/tenant-registration-invitations/v1")
                .secure(true)
                .cookie(accessCookie(reviewer.accountId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(invitationId.toString()));

        mockMvc.perform(withCsrf(
                apiDelete("/tenant-registration-invitations/v1/" + invitationId)
                    .secure(true)
                    .cookie(accessCookie(reviewer.accountId())),
                csrf
            ))
            .andExpect(status().isNoContent());

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitation-previews/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                        """.formatted(token)),
                csrf
            ))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:tenant-registration-invitation-revoked"
            ));

        var expiredCreation = createInvitation(
            issuer.accountId(),
            csrf,
            "Expired invitation"
        );
        var expiredInvitationId = UUID.fromString(expiredCreation.get("id").asText());
        var expiredToken = invitationToken(expiredCreation);
        var expiredCreatedAt = Instant.now().minus(Duration.ofDays(8));
        jdbcTemplate.update(
            """
                UPDATE tenant_registration_invitations
                SET created_at = ?, updated_at = ?, expires_at = ?
                WHERE id = ?
                """,
            expiredCreatedAt,
            expiredCreatedAt,
            expiredCreatedAt.plus(Duration.ofDays(7)),
            expiredInvitationId
        );
        entityManager.clear();

        mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitation-previews/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                        """.formatted(expiredToken)),
                csrf
            ))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:tenant-registration-invitation-expired"
            ));
    }

    @Test
    void accountKindsAreExplicitlySeparatedAtTheBrowserApiBoundary() throws Exception {
        var operator = insertPlatformOperator("matrix-operator");
        var tenantAccount = insertTenantAdministrator();

        mockMvc.perform(apiGet("/locations/v1")
                .secure(true)
                .cookie(accessCookie(operator.accountId())))
            .andExpect(status().isForbidden());
        mockMvc.perform(apiGet("/tenant-registration-invitations/v1")
                .secure(true)
                .cookie(new MockCookie(
                    ACCESS_COOKIE,
                    accessTokenIssuer.issue(tenantAccount).value()
                )))
            .andExpect(status().isForbidden());
    }

    private tools.jackson.databind.JsonNode createInvitation(
        UUID operatorAccountId,
        Cookie csrf,
        String label
    ) throws Exception {
        var creation = mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitations/v1")
                    .secure(true)
                    .cookie(accessCookie(operatorAccountId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("label", label))),
                csrf
            ))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(creation.getResponse().getContentAsByteArray());
    }

    private void assertInvitationPreviews(String token, Cookie csrf) throws Exception {
        mockMvc.perform(withCsrf(
                apiPost("/tenant-registration-invitation-previews/v1")
                    .secure(true)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                        """.formatted(token)),
                csrf
            ))
            .andExpect(status().isOk());
    }

    private StaffPrincipal insertTenantAdministrator() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            accountId,
            tenantId,
            "matrix-tenant-" + accountId,
            "matrix-tenant-" + accountId + "@example.com",
            "fixture-password-hash",
            now,
            now
        );
        return new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
    }

    private static String invitationToken(tools.jackson.databind.JsonNode creation) {
        var link = creation.get("invitationLink").asText();
        return link.substring(link.indexOf('=') + 1);
    }

    private static String registrationJson(
        String token,
        String username,
        String email,
        String passwordConfirmation
    ) {
        return """
            {
              "token":"%s",
              "username":"%s",
              "email":"%s",
              "password":"Correct-Horse-12",
              "passwordConfirmation":"%s"
            }
            """.formatted(token, username, email, passwordConfirmation);
    }

    private OperatorFixture insertPlatformOperator(String prefix) {
        var accountId = UUID.randomUUID();
        var username = prefix + "-" + UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, kind, username, email, password_hash,
                    status, created_at, updated_at
                ) VALUES (?, 'PLATFORM_OPERATOR', ?, ?, ?, 'ENABLED', ?, ?)
                """,
            accountId,
            username,
            username + "@example.com",
            passwordEncoder.encode("Correct-Horse-12"),
            now,
            now
        );
        return new OperatorFixture(accountId, username);
    }

    private MockCookie accessCookie(UUID accountId) {
        return new MockCookie(
            ACCESS_COOKIE,
            accessTokenIssuer.issue(new PlatformOperatorPrincipal(accountId)).value()
        );
    }

    private Cookie bootstrapCsrf() throws Exception {
        var result = mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getCookie(CSRF_COOKIE);
    }

    private static MockHttpServletRequestBuilder withCsrf(
        MockHttpServletRequestBuilder request,
        Cookie csrf
    ) {
        return request.cookie(csrf).header(CSRF_HEADER, csrf.getValue());
    }

    private static MockHttpServletRequestBuilder apiGet(String path) {
        return get(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiDelete(String path) {
        return delete(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
    }

    private record OperatorFixture(UUID accountId, String username) {
    }
}
