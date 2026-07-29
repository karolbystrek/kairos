package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import lombok.NonNull;

public record EncryptedPushValue(
        byte @NonNull [] ciphertext,
        byte @NonNull [] nonce
) {

    public EncryptedPushValue {
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }
}
