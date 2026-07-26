package pl.karolbystrek.kairos.api.integration.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private ExternalIntegration integration;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 128)
    private String normalizedName;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    @Getter(AccessLevel.NONE)
    private Set<ApiKeyScope> scopes = new HashSet<>();

    @OneToMany(mappedBy = "apiKey", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter(AccessLevel.NONE)
    private List<ApiKeyLocationAccess> locationAccess = new ArrayList<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ApiKey issue(
            @NonNull ExternalIntegration integration,
            @NonNull ManagedIntegrationName name,
            @NonNull Set<ApiKeyScope> scopes,
            @NonNull Set<UUID> locationIds,
            Instant expiresAt,
            @NonNull Instant now
    ) {
        if (integration.isArchived()) {
            throw new IllegalStateException("Archived integrations cannot issue API Keys");
        }
        if (locationIds.isEmpty()) {
            throw new IllegalArgumentException("At least one API Key location is required");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("API Key expiration must be in the future");
        }

        var apiKey = new ApiKey();
        apiKey.id = UUID.randomUUID();
        apiKey.integration = integration;
        apiKey.tenantId = integration.getTenantId();
        apiKey.name = name.value();
        apiKey.normalizedName = name.normalizedValue();
        apiKey.scopes.addAll(ApiKeyScope.normalize(scopes));
        apiKey.expiresAt = expiresAt;
        apiKey.createdAt = now;
        locationIds.forEach(locationId -> apiKey.locationAccess.add(
                ApiKeyLocationAccess.grant(apiKey, apiKey.tenantId, locationId)
        ));
        return apiKey;
    }

    public UUID getIntegrationId() {
        return integration.getId();
    }

    public Set<ApiKeyScope> getScopes() {
        return Set.copyOf(scopes);
    }

    public Set<UUID> getLocationIds() {
        return locationAccess.stream()
                .map(ApiKeyLocationAccess::getLocationId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean grants(ApiKeyScope required) {
        return scopes.stream().anyMatch(scope -> scope.grants(required));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(@NonNull Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean canIssueVersionAt(@NonNull Instant now) {
        return !isRevoked() && !isExpiredAt(now) && !integration.isArchived();
    }

    public boolean canAuthenticateAt(@NonNull Instant now) {
        return canIssueVersionAt(now) && integration.isEnabled();
    }

    public void revoke(@NonNull Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
