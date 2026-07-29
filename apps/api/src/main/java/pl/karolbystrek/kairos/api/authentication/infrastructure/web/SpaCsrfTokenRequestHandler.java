package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_COOKIE;

@Component
public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private static final String RAW_TOKEN_ATTRIBUTE =
            SpaCsrfTokenRequestHandler.class.getName() + ".RAW_TOKEN";

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken
    ) {
        var rawToken = csrfToken.get();
        request.setAttribute(RAW_TOKEN_ATTRIBUTE, rawToken);
        xor.handle(request, response, () -> rawToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        var headerValue = request.getHeader(csrfToken.getHeaderName());
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }

        var cookie = WebUtils.getCookie(request, CSRF_COOKIE);
        if (cookie == null || !equalsConstantTime(cookie.getValue(), headerValue)) {
            return null;
        }
        return plain.resolveCsrfTokenValue(request, csrfToken);
    }

    static CsrfToken rawToken(HttpServletRequest request) {
        var token = request.getAttribute(RAW_TOKEN_ATTRIBUTE);
        if (token instanceof CsrfToken csrfToken) {
            return csrfToken;
        }
        throw new IllegalStateException("The current CSRF token is unavailable");
    }

    private static boolean equalsConstantTime(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
