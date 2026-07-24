package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.ACCESS_COOKIE;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        if (ignoresAccessToken(requestPath(request))) {
            return null;
        }

        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (var cookie : cookies) {
            if (ACCESS_COOKIE.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean ignoresAccessToken(String path) {
        return path.equals("/auth/csrf")
                || path.equals("/auth/login")
                || path.equals("/auth/refresh")
                || path.equals("/tenant-registrations")
                || path.equals("/tracked-orders")
                || path.startsWith("/tracked-orders/")
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/");
    }

    private static String requestPath(HttpServletRequest request) {
        var path = request.getRequestURI();
        var contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
