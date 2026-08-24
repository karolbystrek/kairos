package pl.karolbystrek.kairos.api.tenant.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRegistrationInvitationRequest(
    @NotBlank(message = "Invitation label is required")
    @Size(max = 120, message = "Invitation label must not exceed 120 characters")
    String label
) {
}
