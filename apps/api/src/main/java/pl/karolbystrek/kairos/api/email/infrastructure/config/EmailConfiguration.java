package pl.karolbystrek.kairos.api.email.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import pl.karolbystrek.kairos.api.email.infrastructure.security.EmailPayloadCipher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    @Bean
    EmailPayloadCipher emailPayloadCipher(EmailProperties properties, ResourceLoader resources, Environment environment) {
        if (properties.mode() == EmailProperties.Mode.LOG && !environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalStateException("Logging email delivery requires the local or test profile");
        }
        if (properties.mode() == EmailProperties.Mode.RESEND && properties.apiKey().isBlank()) {
            throw new IllegalStateException("Resend API key is required");
        }
        return EmailPayloadCipher.from(resources.getResource(properties.encryptionKeyLocation()));
    }
}
