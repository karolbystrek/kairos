package pl.karolbystrek.kairos.api.authentication.application.model;

import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import java.util.*;
public record CurrentAccountView(UUID accountId, String email, UUID tenantId, TenantRole tenantRole,
                                 LocationAssignmentView assignment, List<String> capabilities) {
    public record LocationAssignmentView(UUID locationId, AssignmentRole role) {}
}
