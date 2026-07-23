package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.model.IssuedSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.ACCESS_COOKIE;
import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.REFRESH_COOKIE;

@Component
@RequiredArgsConstructor
public class AuthenticationCookieService {

    private final Clock clock;

    public void write(HttpServletResponse response, IssuedSession session) {
        append(
            response,
            ACCESS_COOKIE,
            session.accessToken().value(),
            Duration.between(session.accessToken().issuedAt(), session.accessToken().expiresAt())
        );
        append(
            response,
            REFRESH_COOKIE,
            session.refreshCredential(),
            positiveDurationUntil(session.refreshCookieExpiresAt())
        );
    }

    public void clear(HttpServletResponse response) {
        append(response, ACCESS_COOKIE, "", Duration.ZERO);
        append(response, REFRESH_COOKIE, "", Duration.ZERO);
    }

    public String readRefreshCredential(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (var cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void append(HttpServletResponse response, String name, String value, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value)
            .secure(true)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Duration positiveDurationUntil(Instant expiresAt) {
        var remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
