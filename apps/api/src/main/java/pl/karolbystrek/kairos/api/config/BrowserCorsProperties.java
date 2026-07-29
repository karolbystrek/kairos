package pl.karolbystrek.kairos.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kairos.browser-cors")
public record BrowserCorsProperties(
        @NotBlank String customerOrigin,
        @NotBlank String panelOrigin
) {
}
