package pl.karolbystrek.kairos.api.authentication.api.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 200, message = "Email must not exceed 200 characters.") String email,
    @NotBlank(message = "Password is required.")
    @Size(min = 12, message = "Use at least 12 characters.")
    @Size(max = 200, message = "Password must not exceed 200 characters.") String password,
    @NotBlank(message = "Confirm your password.")
    @Size(max = 200, message = "Password confirmation must not exceed 200 characters.") String passwordConfirmation,
    @Size(max = 100) String token
) {
    @AssertTrue(message = "Passwords must match.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirmation);
    }
}
