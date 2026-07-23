package pl.karolbystrek.kairos.api.account.domain.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationAssignment {

    @EmbeddedId
    private LocationAssignmentId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssignmentRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssignmentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LocationAssignment active(
        @NonNull UUID accountId,
        @NonNull UUID locationId,
        @NonNull UUID tenantId,
        @NonNull AssignmentRole role,
        @NonNull Instant now
    ) {
        var assignment = new LocationAssignment();
        assignment.id = new LocationAssignmentId(accountId, locationId);
        assignment.tenantId = tenantId;
        assignment.role = role;
        assignment.status = AssignmentStatus.ACTIVE;
        assignment.createdAt = now;
        assignment.updatedAt = now;
        return assignment;
    }

    public UUID getAccountId() {
        return id.accountId();
    }

    public UUID getLocationId() {
        return id.locationId();
    }
}
