package pl.karolbystrek.kairos.api.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKeyVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @Column(name = "secret_hash", nullable = false, unique = true, length = 64)
    private String secretHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public static ApiKeyVersion issue(
            @NonNull UUID id,
            @NonNull ApiKey apiKey,
            @NonNull String secretHash,
            @NonNull Instant now
    ) {
        var version = new ApiKeyVersion();
        version.id = id;
        version.apiKey = apiKey;
        version.secretHash = secretHash;
        version.issuedAt = now;
        return version;
    }

    public UUID getApiKeyId() {
        return apiKey.getId();
    }

    public boolean isCurrent() {
        return validUntil == null && retiredAt == null;
    }

    public boolean isValidAt(@NonNull Instant now) {
        return retiredAt == null && (validUntil == null || validUntil.isAfter(now));
    }

    public void beginGracePeriod(@NonNull Instant graceEnd) {
        if (!isCurrent()) {
            throw new IllegalStateException("Only the current API Key version can enter a grace period");
        }
        if (!graceEnd.isAfter(issuedAt)) {
            throw new IllegalArgumentException("API Key version grace period must end after issuance");
        }
        validUntil = graceEnd;
    }

    public void retire(@NonNull Instant now) {
        if (retiredAt == null) {
            retiredAt = now;
        }
    }
}
