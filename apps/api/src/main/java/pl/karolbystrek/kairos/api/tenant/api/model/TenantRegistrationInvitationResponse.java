package pl.karolbystrek.kairos.api.tenant.api.model;

import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationInvitationView;

import java.time.Instant;
import java.util.UUID;

public record TenantRegistrationInvitationResponse(
    UUID id,
    String label,
    String issuedByUsername,
    Instant createdAt,
    Instant expiresAt
) {
    public static TenantRegistrationInvitationResponse from(TenantRegistrationInvitationView invitation) {
        return new TenantRegistrationInvitationResponse(
            invitation.id(),
            invitation.label(),
            invitation.issuedByUsername(),
            invitation.createdAt(),
            invitation.expiresAt()
        );
    }
}
