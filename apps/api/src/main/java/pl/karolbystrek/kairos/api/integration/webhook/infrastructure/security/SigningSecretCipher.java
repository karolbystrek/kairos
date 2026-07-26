package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security;

import lombok.NonNull;
import org.springframework.core.io.Resource;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class SigningSecretCipher {

    private static final int ENCRYPTION_KEY_BYTES = 32;
    private static final int SIGNING_SECRET_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    private SigningSecretCipher(byte[] keyBytes) {
        encryptionKey = new SecretKeySpec(keyBytes, "AES");
        secureRandom = new SecureRandom();
    }

    public static SigningSecretCipher from(@NonNull Resource resource) {
        try (var input = resource.getInputStream()) {
            var keyBytes = input.readAllBytes();
            if (keyBytes.length != ENCRYPTION_KEY_BYTES) {
                throw new IllegalStateException(
                        "Webhook signing-secret encryption key must contain exactly 32 bytes"
                );
            }
            return new SigningSecretCipher(keyBytes);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load the configured webhook signing-secret encryption key",
                    exception
            );
        }
    }

    public IssuedSigningSecret issue(@NonNull UUID subscriptionId, @NonNull UUID versionId) {
        var secretBytes = new byte[SIGNING_SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        var nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return new IssuedSigningSecret(
                value,
                transform(Cipher.ENCRYPT_MODE, value.getBytes(StandardCharsets.UTF_8), nonce, subscriptionId, versionId),
                nonce
        );
    }

    public byte[] decrypt(
            byte @NonNull [] encryptedSecret,
            byte @NonNull [] nonce,
            @NonNull UUID subscriptionId,
            @NonNull UUID versionId
    ) {
        return transform(Cipher.DECRYPT_MODE, encryptedSecret, nonce, subscriptionId, versionId);
    }

    private byte[] transform(
            int mode,
            byte[] input,
            byte[] nonce,
            UUID subscriptionId,
            UUID versionId
    ) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, encryptionKey, new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
            cipher.updateAAD(aad(subscriptionId, versionId));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not transform a webhook signing secret", exception);
        }
    }

    private static byte[] aad(UUID subscriptionId, UUID versionId) {
        return (subscriptionId + ":" + versionId).getBytes(StandardCharsets.US_ASCII);
    }
}
