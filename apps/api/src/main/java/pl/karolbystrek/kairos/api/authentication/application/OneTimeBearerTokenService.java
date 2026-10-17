package pl.karolbystrek.kairos.api.authentication.application;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.util.Hashing;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class OneTimeBearerTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedToken generate() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(value, hash(value));
    }

    public String hash(String token) {
        return Hashing.sha256Hex(token);
    }

    public record GeneratedToken(String value, String hash) {
    }
}
