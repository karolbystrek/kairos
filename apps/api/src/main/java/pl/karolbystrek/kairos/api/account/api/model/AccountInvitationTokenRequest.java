package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountInvitationTokenRequest(
    @NotBlank(message = "Invitation token is required")
    @Size(max = 128, message = "Invitation token is not valid")
    String token
) {
}
