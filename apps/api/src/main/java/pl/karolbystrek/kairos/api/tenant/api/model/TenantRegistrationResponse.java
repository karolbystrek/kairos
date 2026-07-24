package pl.karolbystrek.kairos.api.tenant.api.model;

import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationView;

import java.util.UUID;

public record TenantRegistrationResponse(
    UUID tenantId,
    UUID locationId,
    UUID administratorAccountId,
    String username
) {

    public static TenantRegistrationResponse from(TenantRegistrationView registration) {
        return new TenantRegistrationResponse(
            registration.tenantId(),
            registration.locationId(),
            registration.administratorAccountId(),
            registration.username()
        );
    }
}
