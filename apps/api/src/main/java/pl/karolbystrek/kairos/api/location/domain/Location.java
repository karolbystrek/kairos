package pl.karolbystrek.kairos.api.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

    public static final String DEFAULT_TIME_ZONE = "UTC";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    public static Location create(@NonNull UUID tenantId) {
        var location = new Location();
        location.id = UUID.randomUUID();
        location.tenantId = tenantId;
        location.timeZone = DEFAULT_TIME_ZONE;
        return location;
    }
}
