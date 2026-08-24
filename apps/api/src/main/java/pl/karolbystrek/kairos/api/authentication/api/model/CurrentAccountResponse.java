package pl.karolbystrek.kairos.api.authentication.api.model;

import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.authentication.application.model.CurrentAccountView;

import java.util.List;
import java.util.UUID;

public sealed interface CurrentAccountResponse permits CurrentAccountResponse.TenantAccountResponse,
    CurrentAccountResponse.PlatformOperatorResponse {

    UUID accountId();

    String username();

    AccountKind kind();

    List<String> capabilities();

    static CurrentAccountResponse from(CurrentAccountView account) {
        return switch (account) {
            case CurrentAccountView.TenantAccountView tenant -> new TenantAccountResponse(
                tenant.accountId(),
                tenant.username(),
                tenant.kind(),
                tenant.tenantId(),
                tenant.tenantRole(),
                tenant.assignment() == null ? null : LocationAssignmentResponse.from(tenant.assignment()),
                tenant.capabilities()
            );
            case CurrentAccountView.PlatformOperatorView operator -> new PlatformOperatorResponse(
                operator.accountId(),
                operator.username(),
                operator.kind(),
                operator.capabilities()
            );
        };
    }

    record TenantAccountResponse(
        UUID accountId,
        String username,
        AccountKind kind,
        UUID tenantId,
        TenantRole tenantRole,
        LocationAssignmentResponse assignment,
        List<String> capabilities
    ) implements CurrentAccountResponse {
    }

    record PlatformOperatorResponse(
        UUID accountId,
        String username,
        AccountKind kind,
        List<String> capabilities
    ) implements CurrentAccountResponse {
    }

    record LocationAssignmentResponse(
        UUID locationId,
        AssignmentRole role
    ) {
        private static LocationAssignmentResponse from(CurrentAccountView.LocationAssignmentView assignment) {
            return new LocationAssignmentResponse(assignment.locationId(), assignment.role());
        }
    }
}
