package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security;

import lombok.NonNull;

public record IssuedSigningSecret(
        @NonNull String value,
        byte @NonNull [] encryptedValue,
        byte @NonNull [] nonce
) {

    public IssuedSigningSecret {
        encryptedValue = encryptedValue.clone();
        nonce = nonce.clone();
    }
}
