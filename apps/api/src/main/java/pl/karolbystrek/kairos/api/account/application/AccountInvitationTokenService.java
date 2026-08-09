package pl.karolbystrek.kairos.api.account.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
class AccountInvitationTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    GeneratedToken generate() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(value, hash(value));
    }

    String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record GeneratedToken(String value, String hash) {
    }
}
