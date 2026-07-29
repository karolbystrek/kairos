package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import lombok.NonNull;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

public record VapidKeyMaterial(
        @NonNull ECPublicKey publicKey,
        @NonNull ECPrivateKey privateKey,
        byte @NonNull [] applicationServerKey,
        @NonNull String applicationServerKeyBase64,
        @NonNull String fingerprint
) {

    public VapidKeyMaterial {
        applicationServerKey = applicationServerKey.clone();
    }

    @Override
    public byte[] applicationServerKey() {
        return applicationServerKey.clone();
    }
}
