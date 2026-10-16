package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kairos.authentication")
public record AuthenticationProperties(@NotBlank String baseUrl, @NotNull Resource tokenLocation) {}
