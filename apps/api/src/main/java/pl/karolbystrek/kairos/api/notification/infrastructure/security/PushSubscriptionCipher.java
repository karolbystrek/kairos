package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import lombok.NonNull;
import org.springframework.core.io.Resource;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;

public final class PushSubscriptionCipher {

    private static final int ENCRYPTION_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    private PushSubscriptionCipher(byte[] keyBytes) {
        encryptionKey = new SecretKeySpec(keyBytes, "AES");
    }

    public static PushSubscriptionCipher from(@NonNull Resource resource) {
        try (var input = resource.getInputStream()) {
            var keyBytes = input.readAllBytes();
            if (keyBytes.length != ENCRYPTION_KEY_BYTES) {
                throw new IllegalStateException(
                        "Customer Push subscription encryption key must contain exactly 32 bytes"
                );
            }
            return new PushSubscriptionCipher(keyBytes);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load the configured Customer Push subscription encryption key",
                    exception
            );
        }
    }

    public EncryptedPushValue encrypt(
            byte @NonNull [] plaintext,
            @NonNull UUID subscriptionId,
            @NonNull String purpose
    ) {
        var nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return new EncryptedPushValue(
                transform(Cipher.ENCRYPT_MODE, plaintext, nonce, subscriptionId, purpose),
                nonce
        );
    }

    public byte[] decrypt(
            byte @NonNull [] ciphertext,
            byte @NonNull [] nonce,
            @NonNull UUID subscriptionId,
            @NonNull String purpose
    ) {
        return transform(Cipher.DECRYPT_MODE, ciphertext, nonce, subscriptionId, purpose);
    }

    private byte[] transform(
            int mode,
            byte[] input,
            byte[] nonce,
            UUID subscriptionId,
            String purpose
    ) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, encryptionKey, new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
            cipher.updateAAD((subscriptionId + ":" + purpose).getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not transform Customer Push subscription data", exception);
        }
    }
}
