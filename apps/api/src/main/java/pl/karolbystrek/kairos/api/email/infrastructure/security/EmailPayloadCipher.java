package pl.karolbystrek.kairos.api.email.infrastructure.security;

import org.springframework.core.io.Resource;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;

public final class EmailPayloadCipher {
    public record EncryptedPayload(byte[] ciphertext, byte[] nonce) {
        @Override public String toString() { return "EncryptedPayload[redacted]"; }
    }
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    private EmailPayloadCipher(byte[] bytes) { key = new SecretKeySpec(bytes, "AES"); }

    public static EmailPayloadCipher from(Resource resource) {
        try (var input = resource.getInputStream()) {
            var bytes = input.readAllBytes();
            if (bytes.length != 32) throw new IllegalStateException("Email encryption key must contain exactly 32 bytes");
            return new EmailPayloadCipher(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load email encryption key", exception);
        }
    }

    public EncryptedPayload encrypt(UUID id, byte[] plaintext) {
        var nonce = new byte[12];
        random.nextBytes(nonce);
        return new EncryptedPayload(transform(Cipher.ENCRYPT_MODE, id, plaintext, nonce), nonce);
    }

    public byte[] decrypt(UUID id, byte[] ciphertext, byte[] nonce) {
        return transform(Cipher.DECRYPT_MODE, id, ciphertext, nonce);
    }

    private byte[] transform(int mode, UUID id, byte[] input, byte[] nonce) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD((id + ":email").getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not transform encrypted email", exception);
        }
    }
}
