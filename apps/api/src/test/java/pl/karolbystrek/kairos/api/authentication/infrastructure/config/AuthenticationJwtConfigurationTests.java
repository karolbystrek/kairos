package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.AccessTokenIssuer;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.StaffPrincipalJwtAuthenticationConverter;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationJwtConfigurationTests {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String TENANT_ROLE_CLAIM = "tenant_role";

    private AuthenticationProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void configureJwtInfrastructure() {
        properties = properties();
        var configuration = new AuthenticationSecurityConfiguration();
        var keyPair = configuration.authenticationSigningKeyPair(
                properties,
                new DefaultResourceLoader()
        );
        encoder = configuration.jwtEncoder(keyPair);
        decoder = configuration.jwtDecoder(keyPair, properties);
    }

    @Test
    void issuesAndConvertsAStaffAccessToken() {
        var accountId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var expected = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
        var issuer = new AccessTokenIssuer(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var issued = issuer.issue(expected);
        var jwt = decoder.decode(issued.value());
        var authentication = new StaffPrincipalJwtAuthenticationConverter().convert(jwt);

        assertThat(issued.issuedAt()).isEqualTo(NOW);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://api.localhost");
        assertThat(jwt.getAudience()).containsExactly("kairos-panel");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(authentication.getPrincipal()).isEqualTo(expected);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsAnUnexpectedAudience() {
        var token = encode("https://api.localhost", List.of("kairos-panel", "another-audience"));

        assertThatThrownBy(() -> decoder.decode(token))
                .hasMessageContaining("aud");
    }

    @Test
    void rejectsAnUnexpectedIssuer() {
        var token = encode("https://attacker.invalid", List.of("kairos-panel"));

        assertThatThrownBy(() -> decoder.decode(token))
                .hasMessageContaining("iss");
    }

    @Test
    void rejectsAnExpiredAccessToken() {
        var expiredAt = Instant.now().minusSeconds(60);
        var claims = JwtClaimsSet.builder()
                .issuer("https://api.localhost")
                .audience(List.of("kairos-panel"))
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiredAt.minusSeconds(60))
                .expiresAt(expiredAt)
                .claim(TENANT_ID_CLAIM, UUID.randomUUID().toString())
                .claim(TENANT_ROLE_CLAIM, TenantRole.ADMIN.name())
                .build();
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(),
                claims
        )).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token)).hasMessageContaining("expired");
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws NoSuchAlgorithmException {
        var configuration = new AuthenticationSecurityConfiguration();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var otherKeyPair = generator.generateKeyPair();
        var otherEncoder = configuration.jwtEncoder(otherKeyPair);
        var claims = JwtClaimsSet.builder()
                .issuer("https://api.localhost")
                .audience(List.of("kairos-panel"))
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(TENANT_ID_CLAIM, UUID.randomUUID().toString())
                .claim(TENANT_ROLE_CLAIM, TenantRole.ADMIN.name())
                .build();
        var token = otherEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(),
                claims
        )).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token)).hasMessageContaining("signature");
    }

    @Test
    void requiresBothConfiguredKeyLocations() {
        var configuredKeysRequired = new AuthenticationProperties(
                new AuthenticationProperties.Jwt(
                        "https://api.localhost",
                        "kairos-panel",
                        Duration.ofMinutes(5),
                        null,
                        null
                ),
                properties.refresh(),
                properties.password()
        );

        assertThatThrownBy(() -> new AuthenticationSecurityConfiguration().authenticationSigningKeyPair(
                configuredKeysRequired,
                new DefaultResourceLoader()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Both Kairos JWT public and private key locations");
    }

    private String encode(String issuer, List<String> audience) {
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(TENANT_ID_CLAIM, UUID.randomUUID().toString())
                .claim(TENANT_ROLE_CLAIM, TenantRole.MEMBER.name())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static AuthenticationProperties properties() {
        return new AuthenticationProperties(
                new AuthenticationProperties.Jwt(
                        "https://api.localhost",
                        "kairos-panel",
                        Duration.ofMinutes(5),
                        "classpath:keys/test-jwt-public.pem",
                        "classpath:keys/test-jwt-private.pem"
                ),
                new AuthenticationProperties.Refresh(Duration.ofDays(7), Duration.ofDays(30)),
                new AuthenticationProperties.Password(4)
        );
    }
}
