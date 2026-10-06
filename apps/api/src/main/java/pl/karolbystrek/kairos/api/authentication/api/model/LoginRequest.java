package pl.karolbystrek.kairos.api.authentication.api.model;

import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Email @Size(max = 200) String email,
                           @NotBlank @Size(max = 200) String password) {}
