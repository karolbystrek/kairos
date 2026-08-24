package pl.karolbystrek.kairos.api.tenant.api.model;

import pl.karolbystrek.kairos.api.tenant.application.model.CreatedTenantRegistrationInvitation;

import java.time.Instant;
import java.util.UUID;

public record CreatedTenantRegistrationInvitationResponse(
    UUID id,
    String label,
    String issuedByUsername,
    Instant createdAt,
    Instant expiresAt,
    String invitationLink
) {
    public static CreatedTenantRegistrationInvitationResponse from(
        CreatedTenantRegistrationInvitation created,
        String panelOrigin
    ) {
        var invitation = created.invitation();
        var normalizedOrigin = panelOrigin.endsWith("/")
            ? panelOrigin.substring(0, panelOrigin.length() - 1)
            : panelOrigin;
        return new CreatedTenantRegistrationInvitationResponse(
            invitation.id(),
            invitation.label(),
            invitation.issuedByUsername(),
            invitation.createdAt(),
            invitation.expiresAt(),
            normalizedOrigin + "/tenant-registration#invitation=" + created.token()
        );
    }
}
