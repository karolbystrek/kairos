package pl.karolbystrek.kairos.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kairos.app")
public record AppUrlProperties(@NotBlank String url) {
}
