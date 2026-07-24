package pl.karolbystrek.kairos.api.tenant.application.model;

import lombok.NonNull;

import java.util.UUID;

public record TenantRegistrationView(
    @NonNull UUID tenantId,
    @NonNull UUID locationId,
    @NonNull UUID administratorAccountId,
    @NonNull String username
) {
}
