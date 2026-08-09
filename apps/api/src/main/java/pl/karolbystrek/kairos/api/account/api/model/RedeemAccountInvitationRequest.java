package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.karolbystrek.kairos.api.validation.Utf8Size;

public record RedeemAccountInvitationRequest(
    @NotBlank(message = "Invitation token is required")
    @Size(max = 128, message = "Invitation token is not valid")
    String token,

    @NotBlank(message = "Username is required")
    @Size(max = 120, message = "Username must not exceed 120 characters")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must contain at least 12 characters")
    @Utf8Size(max = 72, message = "Password must not exceed 72 UTF-8 bytes")
    String password
) {
}
