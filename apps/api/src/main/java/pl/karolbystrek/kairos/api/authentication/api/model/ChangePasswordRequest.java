package pl.karolbystrek.kairos.api.authentication.api.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 200) String passwordConfirmation) {
    @AssertTrue(message = "Passwords must match")
    public boolean isPasswordConfirmed() { return password != null && password.equals(passwordConfirmation); }
}
