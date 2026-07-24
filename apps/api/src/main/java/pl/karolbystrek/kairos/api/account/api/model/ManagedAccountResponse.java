package pl.karolbystrek.kairos.api.account.api.model;

import pl.karolbystrek.kairos.api.account.application.model.ManagedAccountView;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Instant;
import java.util.UUID;

public record ManagedAccountResponse(
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

    public static ManagedAccountResponse from(ManagedAccountView account) {
        return new ManagedAccountResponse(
            account.id(),
            account.tenantId(),
            account.locationId(),
            account.username(),
            account.email(),
            account.role(),
            account.status(),
            account.createdAt(),
            account.updatedAt()
        );
    }
}
