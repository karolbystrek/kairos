package pl.karolbystrek.kairos.api.authentication.application.model;

import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.util.List;
import java.util.UUID;

public record CurrentAccountView(
    UUID accountId,
    String displayName,
    UUID tenantId,
    TenantRole tenantRole,
    LocationAssignmentView assignment,
    List<String> capabilities
) {

    public record LocationAssignmentView(
        UUID locationId,
        String locationName,
        AssignmentRole role
    ) {
    }
}
