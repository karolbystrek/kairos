package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.util.UUID;

public record StaffAccessContext(
    @NonNull UUID accountId,
    @NonNull UUID tenantId,
    @NonNull TenantRole tenantRole,
    UUID locationId,
    AssignmentRole assignmentRole
) {

    public StaffAccessContext {
        if (tenantRole == TenantRole.ADMIN && (locationId != null || assignmentRole != null)) {
            throw new IllegalArgumentException("Tenant administrators cannot have a location assignment");
        }
        if (tenantRole == TenantRole.MEMBER && (locationId == null || assignmentRole == null)) {
            throw new IllegalArgumentException("Tenant members require a location assignment");
        }
    }

    public boolean isTenantAdmin() {
        return tenantRole == TenantRole.ADMIN;
    }

    public void requireLocationAccess(
        @NonNull UUID resourceTenantId,
        @NonNull UUID resourceLocationId
    ) {
        if (!tenantId.equals(resourceTenantId)) {
            throw new StaffAccessDeniedException("The account cannot access this tenant");
        }
        if (!isTenantAdmin() && !locationId.equals(resourceLocationId)) {
            throw new StaffAccessDeniedException("The account cannot access this location");
        }
    }
}
