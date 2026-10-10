package pl.karolbystrek.kairos.api.account.api.model;

import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationView;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;
import java.util.UUID;

public record AccountInvitationResponse(
    UUID id,
    String email,
    UUID locationId,
    String locationName,
    AssignmentRole role,
    String issuedByEmail,
    Instant createdAt,
    Instant expiresAt
) {
    public static AccountInvitationResponse from(AccountInvitationView invitation) {
        return new AccountInvitationResponse(
            invitation.id(),
            invitation.email(),
            invitation.locationId(),
            invitation.locationName(),
            invitation.role(),
            invitation.issuedByEmail(),
            invitation.createdAt(),
            invitation.expiresAt()
        );
    }
}
