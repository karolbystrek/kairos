package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.util.UUID;

public record CreateAccountInvitationRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 200, message = "Email must not exceed 200 characters.")
    String email,

    @NotNull(message = "Location is required")
    UUID locationId,

    @NotNull(message = "Assignment role is required")
    AssignmentRole role
) {
}
