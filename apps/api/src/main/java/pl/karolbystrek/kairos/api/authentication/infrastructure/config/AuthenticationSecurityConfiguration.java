package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.util.StringUtils;
import pl.karolbystrek.kairos.api.authentication.application.model.PasswordVerificationFallback;

import java.io.IOException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class AuthenticationSecurityConfiguration {

    private static final int MINIMUM_RSA_KEY_SIZE = 2048;
    private static final int PASSWORD_VERIFICATION_FALLBACK_BYTES = 32;

    @Bean
    PasswordEncoder passwordEncoder(AuthenticationProperties properties) {
        return new BCryptPasswordEncoder(properties.password().bcryptStrength());
    }

    @Bean
    PasswordVerificationFallback passwordVerificationFallback(PasswordEncoder passwordEncoder) {
        var candidateBytes = new byte[PASSWORD_VERIFICATION_FALLBACK_BYTES];
        new SecureRandom().nextBytes(candidateBytes);
        var candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(candidateBytes);
        return new PasswordVerificationFallback(candidate, passwordEncoder.encode(candidate));
    }

    @Bean
    KeyPair authenticationSigningKeyPair(
        AuthenticationProperties properties,
        ResourceLoader resourceLoader
    ) {
        var jwt = properties.jwt();
        var hasPublicKey = StringUtils.hasText(jwt.publicKeyLocation());
        var hasPrivateKey = StringUtils.hasText(jwt.privateKeyLocation());

        if (!hasPublicKey || !hasPrivateKey) {
            throw new IllegalStateException(
                "Both Kairos JWT public and private key locations must be configured together"
            );
        }

        return loadKeyPair(
            resourceLoader.getResource(jwt.publicKeyLocation()),
            resourceLoader.getResource(jwt.privateKeyLocation())
        );
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair authenticationSigningKeyPair) {
        var publicKey = (RSAPublicKey) authenticationSigningKeyPair.getPublic();
        var privateKey = (RSAPrivateKey) authenticationSigningKeyPair.getPrivate();
        var rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(keyId(publicKey))
            .build();
        var jwkSource = new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair authenticationSigningKeyPair, AuthenticationProperties properties) {
        var publicKey = (RSAPublicKey) authenticationSigningKeyPair.getPublic();
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();

        var issuer = JwtValidators.createDefaultWithIssuer(properties.jwt().issuer());
        var audience = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            claim -> claim != null
                && claim.size() == 1
                && properties.jwt().audience().equals(claim.getFirst())
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private static KeyPair loadKeyPair(Resource publicKeyResource, Resource privateKeyResource) {
        try (
            var publicKeyInput = publicKeyResource.getInputStream();
            var privateKeyInput = privateKeyResource.getInputStream()
        ) {
            var publicKey = RsaKeyConverters.x509().convert(publicKeyInput);
            var privateKey = RsaKeyConverters.pkcs8().convert(privateKeyInput);
            validateKeyPair(publicKey, privateKey);
            return new KeyPair(publicKey, privateKey);
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load the configured Kairos JWT RSA key pair", exception);
        }
    }

    private static void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (publicKey == null || privateKey == null) {
            throw new IllegalArgumentException("Configured key resources do not contain an RSA key pair");
        }
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException("Configured JWT public and private keys do not form a pair");
        }
        if (publicKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE) {
            throw new IllegalArgumentException("Configured JWT RSA key must be at least 2048 bits");
        }
    }

    private static String keyId(RSAPublicKey publicKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
