package pl.karolbystrek.kairos.api.integration.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_signing_secret_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookSigningSecretVersion {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "BYTEA")
    private byte[] encryptedSecret;

    @Column(name = "encryption_nonce", nullable = false, columnDefinition = "BYTEA")
    private byte[] encryptionNonce;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public static WebhookSigningSecretVersion issue(
            @NonNull UUID id,
            @NonNull UUID subscriptionId,
            byte @NonNull [] encryptedSecret,
            byte @NonNull [] encryptionNonce,
            @NonNull Instant issuedAt
    ) {
        var version = new WebhookSigningSecretVersion();
        version.id = id;
        version.subscriptionId = subscriptionId;
        version.encryptedSecret = encryptedSecret.clone();
        version.encryptionNonce = encryptionNonce.clone();
        version.issuedAt = issuedAt;
        return version;
    }

    public void beginOverlap(@NonNull Instant validUntil) {
        if (!validUntil.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Signing-secret overlap must end after issuance");
        }
        this.validUntil = validUntil;
    }

    public void retire(@NonNull Instant now) {
        if (retiredAt == null) {
            retiredAt = now;
        }
    }

    public boolean isCurrent() {
        return validUntil == null && retiredAt == null;
    }
}
