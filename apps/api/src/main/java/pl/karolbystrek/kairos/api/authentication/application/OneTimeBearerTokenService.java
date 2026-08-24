package pl.karolbystrek.kairos.api.authentication.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class OneTimeBearerTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern PRESENTED_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedToken generate() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(value, hash(value));
    }

    public String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public Optional<String> hashPresented(String token) {
        if (token == null || !PRESENTED_TOKEN_PATTERN.matcher(token).matches()) {
            return Optional.empty();
        }
        return Optional.of(hash(token));
    }

    public record GeneratedToken(String value, String hash) {
    }
}
