package pl.karolbystrek.kairos.api.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    public static RefreshSession start(
        @NonNull UUID accountId,
        @NonNull String refreshTokenHash,
        @NonNull Instant createdAt,
        @NonNull Instant absoluteExpiresAt
    ) {
        var sessionId = UUID.randomUUID();
        return create(sessionId, accountId, refreshTokenHash, sessionId, createdAt, absoluteExpiresAt);
    }

    public RefreshSession replacement(@NonNull String replacementTokenHash, @NonNull Instant now) {
        return create(
            UUID.randomUUID(),
            accountId,
            replacementTokenHash,
            tokenFamilyId,
            now,
            expiresAt
        );
    }

    private static RefreshSession create(
        @NonNull UUID id,
        @NonNull UUID accountId,
        @NonNull String refreshTokenHash,
        @NonNull UUID tokenFamilyId,
        @NonNull Instant createdAt,
        @NonNull Instant expiresAt
    ) {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("A refresh session must expire after it is created");
        }

        var session = new RefreshSession();
        session.id = id;
        session.accountId = accountId;
        session.refreshTokenHash = refreshTokenHash;
        session.tokenFamilyId = tokenFamilyId;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isUnavailableAt(@NonNull Instant now, @NonNull Duration idleLifetime) {
        return revokedAt != null
            || replacedById != null
            || !expiresAt.isAfter(now)
            || !createdAt.plus(idleLifetime).isAfter(now);
    }

    public boolean wasConsumed() {
        return replacedById != null;
    }

    public void consume(@NonNull UUID replacementSessionId, @NonNull Instant now) {
        if (revokedAt != null || replacedById != null) {
            throw new IllegalStateException("Refresh session has already been consumed or revoked");
        }
        lastUsedAt = now;
        revokedAt = now;
        replacedById = replacementSessionId;
    }

    public void revoke(@NonNull Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
