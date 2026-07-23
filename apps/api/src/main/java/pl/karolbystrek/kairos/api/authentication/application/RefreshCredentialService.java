package pl.karolbystrek.kairos.api.authentication.application;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.model.RefreshCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshCredentialService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshCredential generate() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RefreshCredential(value, hash(value));
    }

    public String hash(String credential) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(credential.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
