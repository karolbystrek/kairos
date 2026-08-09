package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.util.UUID;

public record CreateAccountInvitationRequest(
    @NotNull(message = "Location is required")
    UUID locationId,

    @NotNull(message = "Assignment role is required")
    AssignmentRole role
) {
}
