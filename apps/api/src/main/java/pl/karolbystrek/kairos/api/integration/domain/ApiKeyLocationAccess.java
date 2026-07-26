package pl.karolbystrek.kairos.api.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

@Entity
@Table(name = "api_key_location_access")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKeyLocationAccess {

    @EmbeddedId
    private ApiKeyLocationAccessId id;

    @MapsId("apiKeyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    static ApiKeyLocationAccess grant(
            @NonNull ApiKey apiKey,
            @NonNull UUID tenantId,
            @NonNull UUID locationId
    ) {
        var access = new ApiKeyLocationAccess();
        access.id = ApiKeyLocationAccessId.of(apiKey.getId(), locationId);
        access.apiKey = apiKey;
        access.tenantId = tenantId;
        return access;
    }

    public UUID getLocationId() {
        return id.getLocationId();
    }
}
