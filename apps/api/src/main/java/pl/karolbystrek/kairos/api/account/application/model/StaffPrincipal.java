package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.security.Principal;
import java.util.UUID;

public record StaffPrincipal(
    @NonNull UUID accountId,
    @NonNull UUID tenantId,
    @NonNull TenantRole tenantRole
) implements Principal {

    @Override
    public String getName() {
        return accountId.toString();
    }
}
