package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;
import java.util.UUID;

public record AccountInvitationView(
    @NonNull UUID id,
    @NonNull String email,
    @NonNull UUID locationId,
    @NonNull String locationName,
    @NonNull AssignmentRole role,
    @NonNull String issuedByEmail,
    @NonNull Instant createdAt,
    @NonNull Instant expiresAt
) {
}
