package pl.karolbystrek.kairos.api.tenant.application.model;

public record CreatedTenantRegistrationInvitation(
    TenantRegistrationInvitationView invitation,
    String token
) {
}
