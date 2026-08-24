package pl.karolbystrek.kairos.api.authentication.application.model;

import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.util.List;
import java.util.UUID;

public sealed interface CurrentAccountView permits CurrentAccountView.TenantAccountView,
    CurrentAccountView.PlatformOperatorView {

    UUID accountId();

    String username();

    AccountKind kind();

    List<String> capabilities();

    record TenantAccountView(
        UUID accountId,
        String username,
        AccountKind kind,
        UUID tenantId,
        TenantRole tenantRole,
        LocationAssignmentView assignment,
        List<String> capabilities
    ) implements CurrentAccountView {
    }

    record PlatformOperatorView(
        UUID accountId,
        String username,
        AccountKind kind,
        List<String> capabilities
    ) implements CurrentAccountView {
    }

    record LocationAssignmentView(
        UUID locationId,
        AssignmentRole role
    ) {
    }
}
