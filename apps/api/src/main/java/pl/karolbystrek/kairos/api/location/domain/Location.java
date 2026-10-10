package pl.karolbystrek.kairos.api.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.time.Instant;
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

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 240)
    private String normalizedName;


    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LocationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_enabled_at", nullable = false)
    private Instant lastEnabledAt;

    @Column(name = "google_review_url", length = 2048)
    private String googleReviewUrl;

    @Column(name = "review_configuration_id")
    private UUID reviewConfigurationId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public static Location create(
        @NonNull UUID tenantId,
        @NonNull ManagedLocationName name,
        @NonNull Instant now
    ) {
        var location = new Location();
        location.id = UUID.randomUUID();
        location.tenantId = tenantId;
        location.name = name.value();
        location.normalizedName = name.normalizedValue();
        location.timeZone = DEFAULT_TIME_ZONE;
        location.status = LocationStatus.ENABLED;
        location.createdAt = now;
        location.updatedAt = now;
        location.lastEnabledAt = now;
        return location;
    }

    public void configureReviews(String url, @NonNull Instant now) {
        requireNotArchived();
        if (Objects.equals(googleReviewUrl, url)) {
            return;
        }
        googleReviewUrl = url;
        reviewConfigurationId = url == null ? null : UUID.randomUUID();
        updatedAt = now;
    }

    public void rename(@NonNull ManagedLocationName name, @NonNull Instant now) {
        requireNotArchived();
        if (this.name.equals(name.value())) {
            return;
        }
        this.name = name.value();
        normalizedName = name.normalizedValue();
        updatedAt = now;
    }

    public void enable(@NonNull Instant now) {
        requireNotArchived();
        if (isEnabled()) {
            return;
        }
        status = LocationStatus.ENABLED;
        lastEnabledAt = now;
        updatedAt = now;
    }

    public void disable(@NonNull Instant now) {
        requireNotArchived();
        if (isDisabled()) {
            return;
        }
        status = LocationStatus.DISABLED;
        if (googleReviewUrl != null) {
            reviewConfigurationId = UUID.randomUUID();
        }
        updatedAt = now;
    }

    public void archive(@NonNull Instant now) {
        if (isArchived()) {
            return;
        }
        if (!isDisabled()) {
            throw new IllegalStateException("Only a disabled location can be archived");
        }
        status = LocationStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public boolean isEnabled() {
        return status == LocationStatus.ENABLED;
    }

    public boolean isDisabled() {
        return status == LocationStatus.DISABLED;
    }

    public boolean isArchived() {
        return status == LocationStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("Archived locations cannot be changed");
        }
    }
}
