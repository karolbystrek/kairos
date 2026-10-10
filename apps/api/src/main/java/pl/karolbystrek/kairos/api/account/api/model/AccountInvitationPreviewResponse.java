package pl.karolbystrek.kairos.api.account.api.model;

import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationPreview;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;

public record AccountInvitationPreviewResponse(
    String email,
    String locationName,
    AssignmentRole role,
    Instant expiresAt
) {
    public static AccountInvitationPreviewResponse from(AccountInvitationPreview preview) {
        return new AccountInvitationPreviewResponse(
            preview.email(),
            preview.locationName(),
            preview.role(),
            preview.expiresAt()
        );
    }
}
