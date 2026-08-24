package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.util.UUID;

public record StaffPrincipal(
    @NonNull UUID accountId,
    @NonNull UUID tenantId,
    @NonNull TenantRole tenantRole
) implements PanelPrincipal {

    @Override
    public AccountKind kind() {
        return AccountKind.TENANT_ACCOUNT;
    }
}
