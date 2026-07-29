package pl.karolbystrek.kairos.api.notification.domain;

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
@Table(name = "customer_push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPushSubscription {

    @Id
    private UUID id;

    @Column(name = "endpoint_hash", nullable = false, unique = true, length = 64)
    private String endpointHash;

    @Column(name = "endpoint_origin", nullable = false, length = 255)
    private String endpointOrigin;

    @Column(name = "encrypted_endpoint", nullable = false)
    private byte[] encryptedEndpoint;

    @Column(name = "endpoint_nonce", nullable = false)
    private byte[] endpointNonce;

    @Column(name = "p256dh_key", nullable = false)
    private byte[] p256dhKey;

    @Column(name = "encrypted_auth_secret", nullable = false)
    private byte[] encryptedAuthSecret;

    @Column(name = "auth_secret_nonce", nullable = false)
    private byte[] authSecretNonce;

    @Column(name = "vapid_key_fingerprint", nullable = false, length = 64)
    private String vapidKeyFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public static CustomerPushSubscription create(
            @NonNull UUID id,
            @NonNull String endpointHash,
            @NonNull String endpointOrigin,
            byte @NonNull [] encryptedEndpoint,
            byte @NonNull [] endpointNonce,
            byte @NonNull [] p256dhKey,
            byte @NonNull [] encryptedAuthSecret,
            byte @NonNull [] authSecretNonce,
            @NonNull String vapidKeyFingerprint,
            Instant expirationTime,
            @NonNull Instant now
    ) {
        var subscription = new CustomerPushSubscription();
        subscription.id = id;
        subscription.endpointHash = endpointHash;
        subscription.endpointOrigin = endpointOrigin;
        subscription.refresh(
                endpointOrigin,
                encryptedEndpoint,
                endpointNonce,
                p256dhKey,
                encryptedAuthSecret,
                authSecretNonce,
                vapidKeyFingerprint,
                expirationTime,
                now
        );
        subscription.createdAt = now;
        return subscription;
    }

    public void refresh(
            @NonNull String endpointOrigin,
            byte @NonNull [] encryptedEndpoint,
            byte @NonNull [] endpointNonce,
            byte @NonNull [] p256dhKey,
            byte @NonNull [] encryptedAuthSecret,
            byte @NonNull [] authSecretNonce,
            @NonNull String vapidKeyFingerprint,
            Instant expirationTime,
            @NonNull Instant now
    ) {
        this.endpointOrigin = endpointOrigin;
        this.encryptedEndpoint = encryptedEndpoint.clone();
        this.endpointNonce = endpointNonce.clone();
        this.p256dhKey = p256dhKey.clone();
        this.encryptedAuthSecret = encryptedAuthSecret.clone();
        this.authSecretNonce = authSecretNonce.clone();
        this.vapidKeyFingerprint = vapidKeyFingerprint;
        expiresAt = expirationTime;
        updatedAt = now;
        lastSeenAt = now;
    }

    public void checkIn(@NonNull Instant now) {
        lastSeenAt = now;
    }

    public boolean isExpiredAt(@NonNull Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
