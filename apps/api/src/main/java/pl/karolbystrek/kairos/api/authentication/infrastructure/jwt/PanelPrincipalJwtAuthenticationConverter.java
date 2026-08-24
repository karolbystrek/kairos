package pl.karolbystrek.kairos.api.authentication.infrastructure.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.util.List;
import java.util.UUID;

@Component
public class PanelPrincipalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var subject = jwt.getSubject();
        var kindClaim = jwt.getClaimAsString(JwtClaimNames.ACCOUNT_KIND);
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(kindClaim)) {
            throw new BadJwtException("Access token is missing required principal claims");
        }

        try {
            var accountId = UUID.fromString(subject);
            var kind = AccountKind.valueOf(kindClaim);
            return switch (kind) {
                case TENANT_ACCOUNT -> tenantAuthentication(jwt, accountId);
                case PLATFORM_OPERATOR -> operatorAuthentication(jwt, accountId);
            };
        }
        catch (IllegalArgumentException exception) {
            throw new BadJwtException("Access token contains invalid principal claims", exception);
        }
    }

    private static PanelAuthenticationToken tenantAuthentication(Jwt jwt, UUID accountId) {
        var tenantIdClaim = jwt.getClaimAsString(JwtClaimNames.TENANT_ID);
        var tenantRoleClaim = jwt.getClaimAsString(JwtClaimNames.TENANT_ROLE);
        if (!StringUtils.hasText(tenantIdClaim) || !StringUtils.hasText(tenantRoleClaim)) {
            throw new BadJwtException("Access token is missing required principal claims");
        }
        var role = TenantRole.valueOf(tenantRoleClaim);
        var principal = new StaffPrincipal(accountId, UUID.fromString(tenantIdClaim), role);
        return new PanelAuthenticationToken(
            jwt,
            principal,
            List.of(
                new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"),
                new SimpleGrantedAuthority("ROLE_" + role.name())
            )
        );
    }

    private static PanelAuthenticationToken operatorAuthentication(Jwt jwt, UUID accountId) {
        if (jwt.hasClaim(JwtClaimNames.TENANT_ID) || jwt.hasClaim(JwtClaimNames.TENANT_ROLE)) {
            throw new BadJwtException("Access token contains invalid principal claims");
        }
        return new PanelAuthenticationToken(
            jwt,
            new PlatformOperatorPrincipal(accountId),
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );
    }
}
