package pl.karolbystrek.kairos.api.email.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("kairos.email")
public record EmailProperties(
    @DefaultValue("RESEND") Mode mode,
    @DefaultValue("") String apiKey,
    @DefaultValue("file:/run/secrets/email-encryption.bin") String encryptionKeyLocation
) {
    public enum Mode { RESEND, LOG }
    @Override public String toString() { return "EmailProperties[redacted]"; }
}
