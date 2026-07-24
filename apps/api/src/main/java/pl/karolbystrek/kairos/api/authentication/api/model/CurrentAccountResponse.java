package pl.karolbystrek.kairos.api.authentication.api.model;

import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.authentication.application.model.CurrentAccountView;

import java.util.List;
import java.util.UUID;

public record CurrentAccountResponse(
    UUID accountId,
    String username,
    UUID tenantId,
    TenantRole tenantRole,
    LocationAssignmentResponse assignment,
    List<String> capabilities
) {
    public static CurrentAccountResponse from(CurrentAccountView account) {
        var assignment = account.assignment() == null
            ? null
            : LocationAssignmentResponse.from(account.assignment());
        return new CurrentAccountResponse(
            account.accountId(),
            account.username(),
            account.tenantId(),
            account.tenantRole(),
            assignment,
            account.capabilities()
        );
    }

    public record LocationAssignmentResponse(
        UUID locationId,
        AssignmentRole role
    ) {
        private static LocationAssignmentResponse from(CurrentAccountView.LocationAssignmentView assignment) {
            return new LocationAssignmentResponse(
                assignment.locationId(),
                assignment.role()
            );
        }
    }
}
