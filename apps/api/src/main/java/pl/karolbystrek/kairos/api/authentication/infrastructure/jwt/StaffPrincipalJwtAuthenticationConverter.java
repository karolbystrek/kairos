package pl.karolbystrek.kairos.api.authentication.infrastructure.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.util.List;
import java.util.UUID;

@Component
public class StaffPrincipalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var subject = jwt.getSubject();
        var tenantIdClaim = jwt.getClaimAsString(JwtClaimNames.TENANT_ID);
        var tenantRoleClaim = jwt.getClaimAsString(JwtClaimNames.TENANT_ROLE);

        if (!StringUtils.hasText(subject)
                || !StringUtils.hasText(tenantIdClaim)
                || !StringUtils.hasText(tenantRoleClaim)) {
            throw new BadJwtException("Access token is missing required principal claims");
        }

        try {
            var tenantRole = TenantRole.valueOf(tenantRoleClaim);
            var principal = new StaffPrincipal(
                    UUID.fromString(subject),
                    UUID.fromString(tenantIdClaim),
                    tenantRole
            );
            return new StaffAuthenticationToken(
                    jwt,
                    principal,
                    List.of(new SimpleGrantedAuthority("ROLE_" + tenantRole.name()))
            );
        } catch (IllegalArgumentException exception) {
            throw new BadJwtException("Access token contains invalid principal claims", exception);
        }
    }
}
