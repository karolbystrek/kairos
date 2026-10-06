package pl.karolbystrek.kairos.api.authentication.api.model;

import pl.karolbystrek.kairos.api.authentication.application.model.CurrentAccountView;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import java.util.*;
public record CurrentAccountResponse(UUID accountId, String email, UUID tenantId, TenantRole tenantRole,
        CurrentAccountView.LocationAssignmentView assignment, List<String> capabilities) {
    public static CurrentAccountResponse from(CurrentAccountView account) {
        return new CurrentAccountResponse(account.accountId(), account.email(), account.tenantId(),
            account.tenantRole(), account.assignment(), account.capabilities());
    }
}
