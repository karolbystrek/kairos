package pl.karolbystrek.kairos.api.authentication.api.model;

import jakarta.validation.constraints.*;
public record RegistrationRequest(@NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 200) String password,
        @NotBlank @jakarta.validation.constraints.Size(max = 200) String passwordConfirmation, @Size(max = 100) String token,
        @Size(max = 120) String locationName) {
    @AssertTrue(message = "Passwords must match")
    public boolean isPasswordConfirmed() { return password != null && password.equals(passwordConfirmation); }
}
