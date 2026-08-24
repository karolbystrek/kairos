package pl.karolbystrek.kairos.api.account.api;

import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountInvitationApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

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
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID locationId;
    private StaffPrincipal administrator;

    @BeforeEach
    void createAdministratorFixture() {
        tenantId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        administrator = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
        var now = Instant.parse("2026-08-01T12:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
            locationId,
            tenantId,
            "Main restaurant",
            "main restaurant",
            "main restaurant"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            accountId,
            tenantId,
            "invitation-admin-" + accountId,
            "invitation-admin-" + accountId + "@example.com",
            "fixture-password-hash",
            now,
            now
        );
    }

    @Test
    void administratorCreatesOneTimeHashedInvitationForASevenDayAssignment() throws Exception {
        var before = Instant.now();
        var result = mockMvc.perform(withAuthenticationAndCsrf(apiPost("/account-invitations/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"locationId":"%s","role":"MANAGER"}
                    """.formatted(locationId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.locationId").value(locationId.toString()))
            .andExpect(jsonPath("$.locationName").value("Main restaurant"))
            .andExpect(jsonPath("$.role").value("MANAGER"))
            .andExpect(jsonPath("$.invitationLink").value(org.hamcrest.Matchers.startsWith(
                "http://localhost:3001/account-registration#invitation="
            )))
            .andReturn();
        var after = Instant.now();

        var response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        var invitationId = UUID.fromString(response.get("id").asText());
        var createdAt = Instant.parse(response.get("createdAt").asText());
        var expiresAt = Instant.parse(response.get("expiresAt").asText());
        var invitationLink = response.get("invitationLink").asText();
        var rawToken = invitationLink.substring(invitationLink.indexOf("#invitation=") + 12);
        var persisted = jdbcTemplate.queryForMap(
            """
                SELECT tenant_id, location_id, issued_by_account_id, assignment_role,
                       token_hash, state, expires_at, redeemed_account_id
                FROM account_invitations
                WHERE id = ?
                """,
            invitationId
        );
        assertThat(createdAt).isBetween(before, after);
        assertThat(expiresAt).isEqualTo(createdAt.plus(Duration.ofDays(7)));
        assertThat(rawToken).doesNotContain("=").hasSize(43);
        assertThat(persisted.get("tenant_id")).isEqualTo(tenantId);
        assertThat(persisted.get("location_id")).isEqualTo(locationId);
        assertThat(persisted.get("issued_by_account_id")).isEqualTo(administrator.accountId());
        assertThat(persisted.get("assignment_role")).isEqualTo("MANAGER");
        assertThat(persisted.get("state")).isEqualTo("PENDING");
        assertThat(persisted.get("redeemed_account_id")).isNull();
        assertThat(persisted.get("token_hash")).isEqualTo(sha256(rawToken));
        assertThat(persisted.values()).doesNotContain(rawToken);
    }

    @Test
    void anonymousInviteePreviewsAndRedeemsAnInvitationIntoAnImmediateSession() throws Exception {
        var created = createInvitation("OPERATOR");
        var token = tokenFrom(created);
        var csrf = csrfCookie();

        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("token", token))), csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationName").value("Main restaurant"))
            .andExpect(jsonPath("$.role").value("OPERATOR"));

        var redemption = mockMvc.perform(withCsrf(apiPost("/account-invitation-redemptions/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token":"%s",
                      "username":"  Invited.Person  ",
                      "email":"INVITED.PERSON@EXAMPLE.COM",
                      "password":"Secure-Password-12"
                    }
                    """.formatted(token)), csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("invited.person"))
            .andExpect(jsonPath("$.tenantRole").value("MEMBER"))
            .andExpect(jsonPath("$.assignment.locationId").value(locationId.toString()))
            .andExpect(jsonPath("$.assignment.role").value("OPERATOR"))
            .andReturn();

        var accountId = UUID.fromString(objectMapper.readTree(
            redemption.getResponse().getContentAsByteArray()
        ).get("accountId").asText());
        assertThat(redemption.getResponse().getCookie("__Host-access-token")).isNotNull();
        assertThat(redemption.getResponse().getCookie("__Host-refresh-token")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT email FROM accounts WHERE id = ?",
            String.class,
            accountId
        )).isEqualTo("invited.person@example.com");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM account_invitations WHERE id = ?",
            String.class,
            UUID.fromString(created.get("id").asText())
        )).isEqualTo("REDEEMED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT redeemed_account_id FROM account_invitations WHERE id = ?",
            UUID.class,
            UUID.fromString(created.get("id").asText())
        )).isEqualTo(accountId);

        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("token", token))), csrf))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:account-invitation-redeemed"
            ));
    }

    @Test
    void administratorListsAndRevokesOnlyEffectivePendingInvitations() throws Exception {
        var first = createInvitation("MANAGER");
        var second = createInvitation("OPERATOR");

        mockMvc.perform(withAuthentication(apiGet("/account-invitations/v1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].invitationLink").doesNotExist());

        mockMvc.perform(withAuthenticationAndCsrf(apiDelete(
                "/account-invitations/v1/{invitationId}",
                UUID.fromString(first.get("id").asText())
            )))
            .andExpect(status().isNoContent());

        mockMvc.perform(withAuthentication(apiGet("/account-invitations/v1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(second.get("id").asText()));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revocation_reason FROM account_invitations WHERE id = ?",
            String.class,
            UUID.fromString(first.get("id").asText())
        )).isEqualTo("STAFF_REVOKED");
    }

    @Test
    void managerCanInviteOnlyOperatorsAtItsLocationAndOperatorCannotInvite() throws Exception {
        var otherLocationId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
            otherLocationId,
            tenantId,
            "Other restaurant",
            "other restaurant",
            "other restaurant"
        );
        var manager = insertMember(locationId, "MANAGER");
        var operator = insertMember(locationId, "OPERATOR");

        mockMvc.perform(withAuthenticationAndCsrf(
                invitationRequest(locationId, "OPERATOR"),
                manager
            ))
            .andExpect(status().isCreated());
        mockMvc.perform(withAuthenticationAndCsrf(
                invitationRequest(locationId, "MANAGER"),
                manager
            ))
            .andExpect(status().isForbidden());
        mockMvc.perform(withAuthenticationAndCsrf(
                invitationRequest(otherLocationId, "OPERATOR"),
                manager
            ))
            .andExpect(status().isForbidden());
        mockMvc.perform(withAuthenticationAndCsrf(
                invitationRequest(locationId, "OPERATOR"),
                operator
            ))
            .andExpect(status().isForbidden());
    }

    @Test
    void correctableConflictAndSignedInAttemptDoNotConsumeInvitation() throws Exception {
        var created = createInvitation("OPERATOR");
        var invitationId = UUID.fromString(created.get("id").asText());
        var token = tokenFrom(created);
        var csrf = csrfCookie();

        mockMvc.perform(withCsrf(apiPost("/account-invitation-redemptions/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(redemptionJson(
                    token,
                    "invitation-admin-" + administrator.accountId(),
                    "different-" + administrator.accountId() + "@example.com"
                )), csrf))
            .andExpect(status().isConflict());
        assertThat(invitationState(invitationId)).isEqualTo("PENDING");

        mockMvc.perform(withAuthenticationAndCsrf(
                apiPost("/account-invitation-redemptions/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(redemptionJson(
                        token,
                        "signed.in.person",
                        "signed.in.person@example.com"
                    )),
                administrator
            ))
            .andExpect(status().isForbidden());
        assertThat(invitationState(invitationId)).isEqualTo("PENDING");

        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("token", token))), csrf))
            .andExpect(status().isOk());
    }

    @Test
    void expirationIsDerivedAndAnonymousOperationsRequireCsrfWithoutDisclosingUnknownTokens()
        throws Exception {
        var created = createInvitation("OPERATOR");
        var invitationId = UUID.fromString(created.get("id").asText());
        var token = tokenFrom(created);
        var createdAt = Instant.now().minus(Duration.ofDays(8));
        jdbcTemplate.update(
            "UPDATE account_invitations SET created_at = ?, updated_at = ?, expires_at = ? WHERE id = ?",
            createdAt,
            createdAt,
            createdAt.plus(Duration.ofDays(7)),
            invitationId
        );
        entityManager.clear();

        mockMvc.perform(withAuthentication(apiGet("/account-invitations/v1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        var csrf = csrfCookie();
        var preview = apiPost("/account-invitation-previews/v1")
            .secure(true)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(java.util.Map.of("token", token)));
        mockMvc.perform(preview)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-missing"));
        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("token", token))), csrf))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:account-invitation-expired"
            ));
        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "token",
                    "unknown-token"
                ))), csrf))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:account-invitation-invalid"
            ));
        assertThat(invitationState(invitationId)).isEqualTo("PENDING");
    }

    private MockHttpServletRequestBuilder invitationRequest(UUID targetLocationId, String role)
        throws Exception {
        return apiPost("/account-invitations/v1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(java.util.Map.of(
                "locationId",
                targetLocationId,
                "role",
                role
            )));
    }

    private StaffPrincipal insertMember(UUID assignedLocationId, String role) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'MEMBER', 'ENABLED', ?, ?)
                """,
            accountId,
            tenantId,
            "invitation-member-" + accountId,
            "invitation-member-" + accountId + "@example.com",
            "fixture-password-hash",
            now,
            now
        );
        jdbcTemplate.update(
            """
                INSERT INTO location_assignments (
                    account_id, location_id, tenant_id, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            accountId,
            assignedLocationId,
            tenantId,
            role,
            now,
            now
        );
        return new StaffPrincipal(accountId, tenantId, TenantRole.MEMBER);
    }

    private static String redemptionJson(String token, String username, String email) {
        return """
            {
              "token":"%s",
              "username":"%s",
              "email":"%s",
              "password":"Secure-Password-12"
            }
            """.formatted(token, username, email);
    }

    private String invitationState(UUID invitationId) {
        return jdbcTemplate.queryForObject(
            "SELECT state FROM account_invitations WHERE id = ?",
            String.class,
            invitationId
        );
    }

    private tools.jackson.databind.JsonNode createInvitation(String role) throws Exception {
        var result = mockMvc.perform(withAuthenticationAndCsrf(apiPost("/account-invitations/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"locationId":"%s","role":"%s"}
                    """.formatted(locationId, role))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String tokenFrom(tools.jackson.databind.JsonNode created) {
        var link = created.get("invitationLink").asText();
        return link.substring(link.indexOf("#invitation=") + 12);
    }

    private MockHttpServletRequestBuilder withAuthenticationAndCsrf(
        MockHttpServletRequestBuilder request
    ) throws Exception {
        return withAuthenticationAndCsrf(request, administrator);
    }

    private MockHttpServletRequestBuilder withAuthenticationAndCsrf(
        MockHttpServletRequestBuilder request,
        StaffPrincipal principal
    ) throws Exception {
        var csrf = csrfCookie();
        var authenticationToken = new UsernamePasswordAuthenticationToken(
            principal,
            "credentials",
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"))
        );
        return request.secure(true)
            .with(authentication(authenticationToken))
            .cookie(csrf)
            .header(CSRF_HEADER, csrf.getValue());
    }

    private MockHttpServletRequestBuilder withAuthentication(
        MockHttpServletRequestBuilder request
    ) {
        return withAuthentication(request, administrator);
    }

    private static MockHttpServletRequestBuilder withAuthentication(
        MockHttpServletRequestBuilder request,
        StaffPrincipal principal
    ) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(
            principal,
            "credentials",
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"))
        );
        return request.secure(true).with(authentication(authenticationToken));
    }

    private Cookie csrfCookie() throws Exception {
        return mockMvc.perform(apiGet("/auth/v1/csrf").secure(true))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(CSRF_COOKIE);
    }

    private static MockHttpServletRequestBuilder withCsrf(
        MockHttpServletRequestBuilder request,
        Cookie csrf
    ) {
        return request.secure(true).cookie(csrf).header(CSRF_HEADER, csrf.getValue());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static MockHttpServletRequestBuilder apiGet(String path, Object... uriVariables) {
        return get(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiPost(String path, Object... uriVariables) {
        return post(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }

    private static MockHttpServletRequestBuilder apiDelete(String path, Object... uriVariables) {
        return delete(API_CONTEXT_PATH + path, uriVariables).contextPath(API_CONTEXT_PATH);
    }
}
