package pl.karolbystrek.kairos.api.integration.application;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ApiKeyCredentialService {

    private static final String PREFIX = "kairos_key_v1.";
    private static final int SECRET_BYTES = 32;
    private static final int ENCODED_SECRET_LENGTH = 43;

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedApiKeyCredential issue() {
        var versionId = UUID.randomUUID();
        var randomBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(randomBytes);
        var randomValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        var value = PREFIX + versionId + "." + randomValue;
        return new IssuedApiKeyCredential(versionId, value, hash(value));
    }

    public UUID parseVersionId(String credential) {
        if (credential == null || !credential.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid API Key credential");
        }

        var separatorIndex = credential.indexOf('.', PREFIX.length());
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Invalid API Key credential");
        }
        var idValue = credential.substring(PREFIX.length(), separatorIndex);
        var secretValue = credential.substring(separatorIndex + 1);
        if (secretValue.length() != ENCODED_SECRET_LENGTH
                || secretValue.chars().anyMatch(ApiKeyCredentialService::isNotBase64UrlCharacter)) {
            throw new IllegalArgumentException("Invalid API Key credential");
        }

        var versionId = UUID.fromString(idValue);
        if (!versionId.toString().equals(idValue)) {
            throw new IllegalArgumentException("Invalid API Key credential");
        }
        return versionId;
    }

    public boolean matches(String credential, String expectedHash) {
        if (credential == null || expectedHash == null) {
            return false;
        }
        var actual = hash(credential).getBytes(StandardCharsets.US_ASCII);
        var expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String hash(String credential) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(credential.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isNotBase64UrlCharacter(int character) {
        return !(character >= 'A' && character <= 'Z')
                && !(character >= 'a' && character <= 'z')
                && !(character >= '0' && character <= '9')
                && character != '-'
                && character != '_';
    }
}
