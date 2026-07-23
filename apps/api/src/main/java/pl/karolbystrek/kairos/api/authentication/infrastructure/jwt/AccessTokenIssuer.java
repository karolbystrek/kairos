package pl.karolbystrek.kairos.api.authentication.infrastructure.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final AuthenticationProperties properties;
    private final Clock clock;

    public IssuedAccessToken issue(StaffPrincipal principal) {
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(properties.jwt().accessLifetime());
        var claims = JwtClaimsSet.builder()
            .issuer(properties.jwt().issuer())
            .audience(List.of(properties.jwt().audience()))
            .subject(principal.accountId().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim(JwtClaimNames.TENANT_ID, principal.tenantId().toString())
            .claim(JwtClaimNames.TENANT_ROLE, principal.tenantRole().name())
            .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256)
            .type("JWT")
            .build();
        var value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, issuedAt, expiresAt);
    }

    public record IssuedAccessToken(
        String value,
        Instant issuedAt,
        Instant expiresAt
    ) {
    }
}
