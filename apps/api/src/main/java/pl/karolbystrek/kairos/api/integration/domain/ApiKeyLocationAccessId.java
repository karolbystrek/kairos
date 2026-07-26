package pl.karolbystrek.kairos.api.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiKeyLocationAccessId implements Serializable {

    @Column(name = "api_key_id", nullable = false)
    private UUID apiKeyId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    public static ApiKeyLocationAccessId of(@NonNull UUID apiKeyId, @NonNull UUID locationId) {
        return new ApiKeyLocationAccessId(apiKeyId, locationId);
    }
}
