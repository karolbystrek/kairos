package pl.karolbystrek.kairos.api.account.api.model;

import pl.karolbystrek.kairos.api.account.application.model.CreatedAccountInvitation;

import java.time.Instant;
import java.util.UUID;

public record CreatedAccountInvitationResponse(
    UUID id,
    UUID locationId,
    String locationName,
    pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole role,
    String issuedByEmail,
    Instant createdAt,
    Instant expiresAt,
    String invitationLink
) {
    public static CreatedAccountInvitationResponse from(
        CreatedAccountInvitation created,
        String panelOrigin
    ) {
        var invitation = created.invitation();
        var normalizedOrigin = panelOrigin.endsWith("/")
            ? panelOrigin.substring(0, panelOrigin.length() - 1)
            : panelOrigin;
        return new CreatedAccountInvitationResponse(
            invitation.id(),
            invitation.locationId(),
            invitation.locationName(),
            invitation.role(),
            invitation.issuedByEmail(),
            invitation.createdAt(),
            invitation.expiresAt(),
            normalizedOrigin + "/account-registration#invitation=" + created.token()
        );
    }
}
