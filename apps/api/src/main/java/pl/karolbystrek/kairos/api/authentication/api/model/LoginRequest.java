package pl.karolbystrek.kairos.api.authentication.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Username is required")
    @Size(max = 120, message = "Username must not exceed 120 characters")
    String username,

    @NotBlank(message = "Password is required")
    @Size(max = 256, message = "Password is too long")
    String password
) {
}
