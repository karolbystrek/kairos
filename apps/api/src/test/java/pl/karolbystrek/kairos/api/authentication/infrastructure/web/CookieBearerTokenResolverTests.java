package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBearerTokenResolverTests {

    private static final String ACCESS_COOKIE = "__Host-access-token";

    @Test
    void readsOnlyTheAccessCookieOnProtectedRequests() {
        var resolver = new CookieBearerTokenResolver();
        var accessCookie = new Cookie(ACCESS_COOKIE, "access-token");

        assertThat(resolver.resolve(request("/auth/me", accessCookie))).isEqualTo("access-token");
        assertThat(resolver.resolve(request("/auth/csrf", accessCookie))).isNull();
        assertThat(resolver.resolve(request("/auth/login", accessCookie))).isNull();
        assertThat(resolver.resolve(request("/auth/refresh", accessCookie))).isNull();
        assertThat(resolver.resolve(request("/tracked-orders/123", accessCookie))).isNull();
        assertThat(resolver.resolve(request("/actuator/health", accessCookie))).isNull();
    }

    private static MockHttpServletRequest request(String path, Cookie cookie) {
        var contextPath = "/api";
        var requestUri = contextPath + path;
        var request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath(contextPath);
        request.setRequestURI(requestUri);
        request.setCookies(cookie);
        return request;
    }
}
