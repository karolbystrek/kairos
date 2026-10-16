package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kairos.authentication")
/** {@code instanceHost} names the ZITADEL instance when {@code baseUrl}'s host differs, as for a host-run API. */
public record AuthenticationProperties(@NotBlank String baseUrl, @NotNull Resource tokenLocation, String instanceHost) {}
