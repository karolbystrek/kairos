package pl.karolbystrek.kairos.api.integration.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_integrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalIntegration {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 128)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExternalIntegrationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_enabled_at", nullable = false)
    private Instant lastEnabledAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public static ExternalIntegration create(
            @NonNull UUID tenantId,
            @NonNull ManagedIntegrationName name,
            @NonNull Instant now
    ) {
        var integration = new ExternalIntegration();
        integration.id = UUID.randomUUID();
        integration.tenantId = tenantId;
        integration.name = name.value();
        integration.normalizedName = name.normalizedValue();
        integration.status = ExternalIntegrationStatus.ENABLED;
        integration.createdAt = now;
        integration.updatedAt = now;
        integration.lastEnabledAt = now;
        return integration;
    }

    public void rename(@NonNull ManagedIntegrationName name, @NonNull Instant now) {
        requireNotArchived();
        if (this.name.equals(name.value())) {
            return;
        }
        this.name = name.value();
        normalizedName = name.normalizedValue();
        updatedAt = now;
    }

    public void changeStatus(@NonNull ExternalIntegrationStatus target, @NonNull Instant now) {
        requireNotArchived();
        if (target == ExternalIntegrationStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archiving requires the integration removal operation");
        }
        if (status == target) {
            return;
        }
        status = target;
        updatedAt = now;
        if (target == ExternalIntegrationStatus.ENABLED) {
            lastEnabledAt = now;
        }
    }

    public void archive(@NonNull Instant now) {
        if (status == ExternalIntegrationStatus.ARCHIVED) {
            return;
        }
        status = ExternalIntegrationStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public boolean isEnabled() {
        return status == ExternalIntegrationStatus.ENABLED;
    }

    public boolean isArchived() {
        return status == ExternalIntegrationStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("Archived integrations cannot be changed");
        }
    }
}
