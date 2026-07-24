package pl.karolbystrek.kairos.api.account.application.model;

import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;
import java.util.UUID;

public record ManagedAccountView(
    UUID id,
    UUID tenantId,
    UUID locationId,
    String username,
    String email,
    AssignmentRole role,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
