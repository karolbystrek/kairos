package pl.karolbystrek.kairos.api.integration.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyAuthenticationService;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyAuthenticationService authenticationService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var bearer = (BearerTokenAuthenticationToken) authentication;
        var principal = authenticationService.authenticate(bearer.getToken());
        var authorities = principal.scopes().stream()
                .map(ApiKeyScope::externalValue)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
        return new ExternalApiKeyAuthenticationToken(principal, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
