package pl.karolbystrek.kairos.api.integration.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;

import java.util.Collection;

final class ExternalApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;

    ExternalApiKeyAuthenticationToken(
            ApiKeyPrincipal principal,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public ApiKeyPrincipal getPrincipal() {
        return principal;
    }
}
