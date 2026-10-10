package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;

public record AccountInvitationPreview(
    @NonNull String email,
    @NonNull String locationName,
    @NonNull AssignmentRole role,
    @NonNull Instant expiresAt
) {
}
