package pl.karolbystrek.kairos.api.tenant.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.karolbystrek.kairos.api.validation.Utf8Size;

public record TenantRegistrationRequest(
    @NotBlank(message = "Tenant name is required")
    @Size(max = 120, message = "Tenant name must not exceed 120 characters")
    String tenantName,

    @NotBlank(message = "Location name is required")
    @Size(max = 120, message = "Location name must not exceed 120 characters")
    String locationName,

    @Valid
    @NotNull(message = "Administrator is required")
    Administrator administrator
) {

    public record Administrator(
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
        String password,

        @NotBlank(message = "Display name is required")
        @Size(max = 120, message = "Display name must not exceed 120 characters")
        String displayName
    ) {
    }
}
