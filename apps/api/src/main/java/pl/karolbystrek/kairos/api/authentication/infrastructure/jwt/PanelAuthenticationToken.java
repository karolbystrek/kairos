package pl.karolbystrek.kairos.api.authentication.infrastructure.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;

import java.util.Collection;

final class PanelAuthenticationToken extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final PanelPrincipal principal;

    PanelAuthenticationToken(
        Jwt jwt,
        PanelPrincipal principal,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.jwt = jwt;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public PanelPrincipal getPrincipal() {
        return principal;
    }

    Jwt getJwt() {
        return jwt;
    }
}
