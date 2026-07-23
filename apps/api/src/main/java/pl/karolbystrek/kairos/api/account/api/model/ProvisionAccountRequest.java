package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

public record ProvisionAccountRequest(
    @NotBlank(message = "Username is required")
    @Size(max = 120, message = "Username must not exceed 120 characters")
    String username,

    @Email(message = "Email must be valid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 72, message = "Password must contain between 12 and 72 characters")
    String password,

    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name must not exceed 120 characters")
    String displayName,

    @NotNull(message = "Assignment role is required")
    AssignmentRole role
) {
}
