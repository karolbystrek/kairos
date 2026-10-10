package pl.karolbystrek.kairos.api.account.api;

import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
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
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
class AccountInvitationApiIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID locationId;
    private StaffPrincipal administrator;

    @BeforeEach
    void createAdministratorFixture() {
        Mockito.when(identityProvider.createUser(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenAnswer(call -> call.getArgument(0));
        Mockito.doAnswer(call -> new ZitadelClient.ProviderSession(
                UUID.randomUUID().toString(), call.getArgument(0)))
            .when(identityProvider).signIn(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        tenantId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        administrator = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
        var now = Instant.parse("2026-08-01T12:00:00Z");
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
            locationId,
            tenantId,
            "Main restaurant",
            "main restaurant"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            "invitation-admin-" + accountId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Test
    void administratorCreatesOneTimeHashedInvitationForASevenDayAssignment() throws Exception {
        var before = Instant.now();
        var result = mockMvc.perform(withAuthenticationAndCsrf(apiPost("/account-invitations/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"locationId":"%s","role":"MANAGER","email":"INVITED.PERSON@EXAMPLE.COM"}
                    """.formatted(locationId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.locationId").value(locationId.toString()))
            .andExpect(jsonPath("$.locationName").value("Main restaurant"))
            .andExpect(jsonPath("$.role").value("MANAGER"))
            .andExpect(jsonPath("$.email").value("invited.person@example.com"))
            .andExpect(jsonPath("$.invitationLink").value(Matchers.startsWith(
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
                       email, token_hash, state, expires_at, redeemed_account_id
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
        assertThat(persisted.get("email")).isEqualTo("invited.person@example.com");
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
                .content(objectMapper.writeValueAsString(Map.of("token", token))), csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationName").value("Main restaurant"))
            .andExpect(jsonPath("$.role").value("OPERATOR"))
            .andExpect(jsonPath("$.email").value("invited.person@example.com"));

        var redemption = mockMvc.perform(withCsrf(apiPost("/account-invitation-redemptions/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token":"%s",
                      "email":"INVITED.PERSON@EXAMPLE.COM",
                      "password":"Secure-Password-12",
                      "passwordConfirmation":"Secure-Password-12"
                    }
                    """.formatted(token)), csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("invited.person@example.com"))
            .andExpect(jsonPath("$.tenantRole").value("MEMBER"))
            .andExpect(jsonPath("$.assignment.locationId").value(locationId.toString()))
            .andExpect(jsonPath("$.assignment.role").value("OPERATOR"))
            .andReturn();

        var accountId = UUID.fromString(objectMapper.readTree(
            redemption.getResponse().getContentAsByteArray()
        ).get("accountId").asText());
        assertThat(redemption.getResponse().getCookie("__Host-session")).isNotNull();
        assertThat(redemption.getResponse().getCookie("__Host-refresh-token")).isNull();
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
                .content(objectMapper.writeValueAsString(Map.of("token", token))), csrf))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:account-invitation-redeemed"
            ));
    }

    @Test
    void mismatchedEmailDoesNotProvisionIdentityOrConsumeInvitation() throws Exception {
        var created = createInvitation("OPERATOR");
        mockMvc.perform(withCsrf(apiPost("/account-invitation-redemptions/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(redemptionJson(tokenFrom(created), "", "someone.else@example.com")), csrfCookie()))
            .andExpect(status().isBadRequest());
        Mockito.verify(identityProvider, Mockito.never()).createUser(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        assertThat(invitationState(UUID.fromString(created.get("id").asText()))).isEqualTo("PENDING");
    }

    @Test
    void invitationRequiresValidRecipientEmail() throws Exception {
        for (var email : List.of("", "not-an-email", "a".repeat(201) + "@example.com")) {
            mockMvc.perform(withAuthenticationAndCsrf(apiPost("/account-invitations/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("locationId", locationId,
                        "role", "OPERATOR", "email", email)))))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(withAuthenticationAndCsrf(apiPost("/account-invitations/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("locationId", locationId, "role", "OPERATOR")))))
            .andExpect(status().isBadRequest());
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
            "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
            otherLocationId,
            tenantId,
            "Other restaurant",
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
                    "invitation-admin-" + administrator.accountId() + "@example.com"
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
                .content(objectMapper.writeValueAsString(Map.of("token", token))), csrf))
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
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt.plus(Duration.ofDays(7))),
            invitationId
        );

        mockMvc.perform(withAuthentication(apiGet("/account-invitations/v1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        var csrf = csrfCookie();
        var preview = apiPost("/account-invitation-previews/v1")
            .secure(true)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token)));
        mockMvc.perform(preview)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-missing"));
        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))), csrf))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.type").value(
                "urn:kairos:problem:account-invitation-expired"
            ));
        mockMvc.perform(withCsrf(apiPost("/account-invitation-previews/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
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
            .content(objectMapper.writeValueAsString(Map.of(
                "locationId",
                targetLocationId,
                "role",
                role,
                "email",
                "invited.person@example.com"
            )));
    }

    private StaffPrincipal insertMember(UUID assignedLocationId, String role) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'MEMBER', 'ENABLED', ?, ?)
                """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            "invitation-member-" + accountId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
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
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return new StaffPrincipal(accountId, tenantId, TenantRole.MEMBER);
    }

    private static String redemptionJson(String token, String ignored, String email) {
        return """
            {
              "token":"%s",
              "email":"%s",
              "password":"Secure-Password-12",
                      "passwordConfirmation":"Secure-Password-12"
            }
            """.formatted(token, email);
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
                    {"locationId":"%s","role":"%s","email":"invited.person@example.com"}
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
