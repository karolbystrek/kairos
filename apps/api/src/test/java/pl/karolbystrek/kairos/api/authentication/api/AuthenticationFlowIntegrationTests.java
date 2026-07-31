package pl.karolbystrek.kairos.api.authentication.api;

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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.application.RefreshCredentialService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class AuthenticationFlowIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    private static final String API_CONTEXT_PATH = "/api";
    private static final String PASSWORD = "Correct-Horse-12";
    private static final String ACCESS_COOKIE = "__Host-access-token";
    private static final String REFRESH_COOKIE = "__Host-refresh-token";
    private static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshCredentialService refreshCredentialService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AuthenticationProperties authenticationProperties;

    @Test
    void logsInWithCsrfAndUsesSecureHostCookiesForTheCurrentAccount() throws Exception {
        var operator = createActiveOperator("login");
        var initialCsrf = bootstrapCsrf("192.0.2.10");

        var login = login(
            "  " + operator.username().toUpperCase() + "  ",
            PASSWORD,
            initialCsrf,
            "192.0.2.10"
        );

        login.result()
            .getResponse();
        assertCurrentOperator(login.result(), operator);
        assertAuthenticationCookie(login.session().access());
        assertAuthenticationCookie(login.session().refresh());
        assertReadableCsrfCookie(login.session().csrf());
        assertThat(login.session().csrf().getValue()).isNotEqualTo(initialCsrf.getValue());

        mockMvc.perform(apiGet("/auth/v1/me")
                .secure(true)
                .cookie(login.session().access()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(operator.accountId().toString()))
            .andExpect(jsonPath("$.username").value(operator.username()))
            .andExpect(jsonPath("$.tenantId").value(operator.tenantId().toString()))
            .andExpect(jsonPath("$.tenantRole").value("MEMBER"))
            .andExpect(jsonPath("$.assignment.locationId").value(operator.locationId().toString()))
            .andExpect(jsonPath("$.assignment.role").value("OPERATOR"))
            .andExpect(jsonPath("$.capabilities[0]").value("MANAGE_ORDERS"));
    }

    @Test
    void returnsTheSamePublicFailureForUnknownWrongDisabledPasswordlessAndIneligibleAccounts() throws Exception {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var passwordHash = passwordEncoder.encode(PASSWORD);
        var suffix = UUID.randomUUID().toString();

        var activeUsername = "active-" + suffix;
        var activeId = insertAccount(
            tenantId, activeUsername, passwordHash, TenantRole.MEMBER, AccountStatus.ACTIVE
        );
        insertAssignment(activeId, tenantId, locationId, AssignmentRole.OPERATOR, AssignmentStatus.ACTIVE);

        var disabledUsername = "disabled-" + suffix;
        insertAccount(
            tenantId, disabledUsername, passwordHash, TenantRole.ADMIN, AccountStatus.DISABLED
        );

        var passwordlessUsername = "passwordless-" + suffix;
        insertAccount(
            tenantId, passwordlessUsername, null, TenantRole.ADMIN, AccountStatus.ACTIVE
        );

        var unassignedUsername = "unassigned-" + suffix;
        insertAccount(
            tenantId, unassignedUsername, passwordHash, TenantRole.MEMBER, AccountStatus.ACTIVE
        );

        var suspendedUsername = "suspended-" + suffix;
        var suspendedId = insertAccount(
            tenantId, suspendedUsername, passwordHash, TenantRole.MEMBER, AccountStatus.ACTIVE
        );
        insertAssignment(suspendedId, tenantId, locationId, AssignmentRole.OPERATOR, AssignmentStatus.SUSPENDED);

        var csrf = bootstrapCsrf("192.0.2.20");
        var responses = List.of(
            invalidLogin("unknown-" + suffix, PASSWORD, csrf, "192.0.2.21"),
            invalidLogin(activeUsername, "Wrong-Password-12", csrf, "192.0.2.22"),
            invalidLogin(disabledUsername, PASSWORD, csrf, "192.0.2.23"),
            invalidLogin(passwordlessUsername, PASSWORD, csrf, "192.0.2.24"),
            invalidLogin(unassignedUsername, PASSWORD, csrf, "192.0.2.25"),
            invalidLogin(suspendedUsername, PASSWORD, csrf, "192.0.2.26")
        );

        assertThat(responses).containsOnly(responses.getFirst());
    }

    @Test
    void rejectsAStaleCsrfHeaderAndRotatesCsrfAfterLoginAndLogout() throws Exception {
        var operator = createActiveOperator("csrf");
        var initialCsrf = bootstrapCsrf("192.0.2.30");
        var login = login(operator.username(), PASSWORD, initialCsrf, "192.0.2.30");

        mockMvc.perform(client(apiPost("/auth/v1/logout")
                .secure(true)
                .cookie(login.session().access(), login.session().refresh(), login.session().csrf())
                .header(CSRF_HEADER, initialCsrf.getValue()), "192.0.2.30"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:kairos:problem:csrf-token-invalid"));

        var logout = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/logout")
                .secure(true)
                .cookie(login.session().access(), login.session().refresh()),
            login.session().csrf(),
            "192.0.2.30"
        ))
            .andExpect(status().isNoContent())
            .andReturn();

        assertClearedAuthenticationCookies(logout);
        var logoutCsrf = activeCsrfResponseCookie(logout);
        assertReadableCsrfCookie(logoutCsrf);
        assertThat(logoutCsrf.getValue()).isNotEqualTo(login.session().csrf().getValue());
    }

    @Test
    void rotatesRefreshCredentialsAndRevokesTheFamilyWhenOneIsReplayed() throws Exception {
        var operator = createActiveOperator("refresh");
        var login = login(
            operator.username(),
            PASSWORD,
            bootstrapCsrf("192.0.2.40"),
            "192.0.2.40"
        );
        var initialRefresh = login.session().refresh().getValue();
        var familyId = sessionId(initialRefresh);

        var rotation = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/refresh")
                .secure(true)
                .cookie(login.session().access(), login.session().refresh()),
            login.session().csrf(),
            "192.0.2.40"
        ))
            .andExpect(status().isNoContent())
            .andReturn();
        var replacementAccess = activeResponseCookie(rotation, ACCESS_COOKIE);
        var replacementRefresh = activeResponseCookie(rotation, REFRESH_COOKIE);
        assertAuthenticationCookie(replacementAccess);
        assertAuthenticationCookie(replacementRefresh);
        assertThat(replacementAccess.getValue()).isNotEqualTo(login.session().access().getValue());
        assertThat(replacementRefresh.getValue()).isNotEqualTo(initialRefresh);
        assertThat(sessionId(replacementRefresh.getValue())).isNotEqualTo(familyId);
        assertThat(sessionFamilyId(replacementRefresh.getValue())).isEqualTo(familyId);
        assertThat(isSessionRevoked(initialRefresh)).isTrue();
        assertThat(replacedById(initialRefresh)).isEqualTo(sessionId(replacementRefresh.getValue()));

        var replay = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/refresh")
                .secure(true)
                .cookie(login.session().refresh()),
            login.session().csrf(),
            "192.0.2.40"
        ))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("The refresh credential is invalid or expired"))
            .andReturn();
        assertClearedAuthenticationCookies(replay);
        assertThat(activeSessionsInFamily(familyId)).isZero();

        mockMvc.perform(withCsrf(
            apiPost("/auth/v1/refresh")
                .secure(true)
                .cookie(replacementRefresh),
            login.session().csrf(),
            "192.0.2.40"
        ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOneSessionAndLogoutAllRevokesEverySession() throws Exception {
        var operator = createActiveOperator("logout");
        var first = login(
            operator.username(), PASSWORD, bootstrapCsrf("192.0.2.50"), "192.0.2.50"
        );
        var second = login(
            operator.username(), PASSWORD, bootstrapCsrf("192.0.2.51"), "192.0.2.51"
        );

        var logout = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/logout")
                .secure(true)
                .cookie(first.session().access(), first.session().refresh()),
            first.session().csrf(),
            "192.0.2.50"
        ))
            .andExpect(status().isNoContent())
            .andReturn();
        assertClearedAuthenticationCookies(logout);
        assertThat(isSessionRevoked(first.session().refresh().getValue())).isTrue();
        assertThat(isSessionRevoked(second.session().refresh().getValue())).isFalse();

        var logoutAll = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/logout-all")
                .secure(true)
                .cookie(second.session().access(), second.session().refresh()),
            second.session().csrf(),
            "192.0.2.51"
        ))
            .andExpect(status().isNoContent())
            .andReturn();
        assertClearedAuthenticationCookies(logoutAll);
        assertThat(activeSessionsForAccount(operator.accountId())).isZero();

        var rotatedCsrf = activeCsrfResponseCookie(logoutAll);
        mockMvc.perform(withCsrf(
            apiPost("/auth/v1/refresh")
                .secure(true)
                .cookie(second.session().refresh()),
            rotatedCsrf,
            "192.0.2.51"
        ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredAccessTokenDoesNotBlockCsrfRefreshOrAnonymousTracking() throws Exception {
        var operator = createActiveOperator("expired");
        var login = login(
            operator.username(), PASSWORD, bootstrapCsrf("192.0.2.60"), "192.0.2.60"
        );
        var expiredAccess = new Cookie(ACCESS_COOKIE, expiredAccessToken(operator));

        mockMvc.perform(apiGet("/auth/v1/me")
                .secure(true)
                .cookie(expiredAccess))
            .andExpect(status().isUnauthorized());

        var csrfBootstrap = mockMvc.perform(apiGet("/auth/v1/csrf")
                .secure(true)
                .cookie(expiredAccess))
            .andExpect(status().isOk())
            .andReturn();
        var csrf = activeCsrfResponseCookie(csrfBootstrap);

        mockMvc.perform(withCsrf(
            apiPost("/auth/v1/refresh")
                .secure(true)
                .cookie(expiredAccess, login.session().refresh()),
            csrf,
            "192.0.2.60"
        ))
            .andExpect(status().isNoContent());

        var trackingReference = insertTrackedOrder(operator.locationId());
        mockMvc.perform(apiGet("/tracked-orders/v1/{trackingReference}", trackingReference)
            .secure(true)
            .cookie(expiredAccess))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));

    }

    private LoginResult login(
        String username,
        String password,
        Cookie csrf,
        String clientAddress
    ) throws Exception {
        var result = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password)),
            csrf,
            clientAddress
        ))
            .andExpect(status().isOk())
            .andReturn();
        return new LoginResult(
            result,
            new BrowserSession(
                activeResponseCookie(result, ACCESS_COOKIE),
                activeResponseCookie(result, REFRESH_COOKIE),
                activeCsrfResponseCookie(result)
            )
        );
    }

    private String invalidLogin(
        String username,
        String password,
        Cookie csrf,
        String clientAddress
    ) throws Exception {
        var result = mockMvc.perform(withCsrf(
            apiPost("/auth/v1/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password)),
            csrf,
            clientAddress
        ))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Invalid username or password"))
            .andReturn();
        assertThat(responseCookies(result, ACCESS_COOKIE)).isEmpty();
        assertThat(responseCookies(result, REFRESH_COOKIE)).isEmpty();
        return result.getResponse().getContentAsString();
    }

    private Cookie bootstrapCsrf(String clientAddress) throws Exception {
        var result = mockMvc.perform(client(
            apiGet("/auth/v1/csrf").secure(true),
            clientAddress
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();
        var csrf = activeCsrfResponseCookie(result);
        assertReadableCsrfCookie(csrf);
        return csrf;
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

    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post(API_CONTEXT_PATH + path).contextPath(API_CONTEXT_PATH);
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

    private static String loginJson(String username, String password) {
        return """
            {"username":"%s","password":"%s"}
            """.formatted(username, password);
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

    private static void assertAuthenticationCookie(MockCookie cookie) {
        assertThat(cookie.getName()).startsWith("__Host-");
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isPositive();
    }

    private static void assertReadableCsrfCookie(Cookie cookie) {
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
    }

    private static void assertClearedAuthenticationCookies(MvcResult result) {
        for (var name : List.of(ACCESS_COOKIE, REFRESH_COOKIE)) {
            var cleared = responseCookies(result, name).stream()
                .filter(cookie -> cookie.getMaxAge() == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No cleared response cookie named " + name));
            assertThat(cleared.getValue()).isEmpty();
            assertThat(cleared.getSecure()).isTrue();
            assertThat(cleared.isHttpOnly()).isTrue();
            assertThat(cleared.getSameSite()).isEqualTo("Lax");
            assertThat(cleared.getPath()).isEqualTo("/");
            assertThat(cleared.getDomain()).isNull();
        }
    }

    private static void assertCurrentOperator(MvcResult result, AccountFixture operator) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getContentAsString())
            .contains(operator.accountId().toString())
            .contains(operator.username())
            .contains(operator.tenantId().toString())
            .contains(operator.locationId().toString())
            .contains("OPERATOR")
            .contains("MANAGE_ORDERS");
    }

    private AccountFixture createActiveOperator(String prefix) {
        var tenantId = insertTenant();
        var locationId = insertLocation(tenantId);
        var username = prefix + "-operator-" + UUID.randomUUID();
        var accountId = insertAccount(
            tenantId,
            username,
            passwordEncoder.encode(PASSWORD),
            TenantRole.MEMBER,
            AccountStatus.ACTIVE
        );
        insertAssignment(accountId, tenantId, locationId, AssignmentRole.OPERATOR, AssignmentStatus.ACTIVE);
        return new AccountFixture(tenantId, locationId, accountId, username);
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        return tenantId;
    }

    private UUID insertLocation(UUID tenantId) {
        var locationId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
            locationId,
            tenantId
        );
        return locationId;
    }

    private UUID insertAccount(
        UUID tenantId,
        String username,
        String passwordHash,
        TenantRole role,
        AccountStatus status
    ) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?)
                """,
            accountId,
            tenantId,
            username,
            passwordHash,
            role.name(),
            status.name(),
            now,
            now
        );
        return accountId;
    }

    private void insertAssignment(
        UUID accountId,
        UUID tenantId,
        UUID locationId,
        AssignmentRole role,
        AssignmentStatus status
    ) {
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO location_assignments (
                    account_id, location_id, tenant_id, role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            accountId,
            locationId,
            tenantId,
            role.name(),
            status.name(),
            now,
            now
        );
    }

    private UUID insertTrackedOrder(UUID locationId) {
        var orderId = UUID.randomUUID();
        var trackingReference = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO orders (
                    id, location_id, tracking_reference, label, status, created_at, updated_at
                ) VALUES (?, ?, ?, '42', 'READY', ?, ?)
                """,
            orderId,
            locationId,
            trackingReference,
            now,
            now
        );
        return trackingReference;
    }

    private UUID sessionId(String rawCredential) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM sessions WHERE refresh_token_hash = ?",
            UUID.class,
            refreshCredentialService.hash(rawCredential)
        );
    }

    private UUID sessionFamilyId(String rawCredential) {
        return jdbcTemplate.queryForObject(
            "SELECT token_family_id FROM sessions WHERE refresh_token_hash = ?",
            UUID.class,
            refreshCredentialService.hash(rawCredential)
        );
    }

    private UUID replacedById(String rawCredential) {
        return jdbcTemplate.queryForObject(
            "SELECT replaced_by_id FROM sessions WHERE refresh_token_hash = ?",
            UUID.class,
            refreshCredentialService.hash(rawCredential)
        );
    }

    private boolean isSessionRevoked(String rawCredential) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM sessions WHERE refresh_token_hash = ?",
            Boolean.class,
            refreshCredentialService.hash(rawCredential)
        ));
    }

    private int activeSessionsInFamily(UUID familyId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE token_family_id = ? AND revoked_at IS NULL",
            Integer.class,
            familyId
        );
    }

    private int activeSessionsForAccount(UUID accountId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE account_id = ? AND revoked_at IS NULL",
            Integer.class,
            accountId
        );
    }

    private String expiredAccessToken(AccountFixture account) {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder()
            .issuer(authenticationProperties.jwt().issuer())
            .audience(List.of(authenticationProperties.jwt().audience()))
            .subject(account.accountId().toString())
            .issuedAt(now.minus(10, ChronoUnit.MINUTES))
            .expiresAt(now.minus(5, ChronoUnit.MINUTES))
            .id(UUID.randomUUID().toString())
            .claim("tenant_id", account.tenantId().toString())
            .claim("tenant_role", TenantRole.MEMBER.name())
            .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private record AccountFixture(
        UUID tenantId,
        UUID locationId,
        UUID accountId,
        String username
    ) {
    }

    private record BrowserSession(MockCookie access, MockCookie refresh, Cookie csrf) {
    }

    private record LoginResult(MvcResult result, BrowserSession session) {
    }
}
