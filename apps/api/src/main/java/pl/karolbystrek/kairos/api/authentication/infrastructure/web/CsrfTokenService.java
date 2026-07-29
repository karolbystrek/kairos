package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CsrfTokenService {

    private final CsrfTokenRepository tokenRepository;

    public CsrfToken current(HttpServletRequest request) {
        return SpaCsrfTokenRequestHandler.rawToken(request);
    }

    public CsrfToken rotate(HttpServletRequest request, HttpServletResponse response) {
        tokenRepository.saveToken(null, request, response);
        var token = tokenRepository.generateToken(request);
        tokenRepository.saveToken(token, request, response);
        return token;
    }
}
