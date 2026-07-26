package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security.SigningSecretCipher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfiguration {

    @Bean
    SigningSecretCipher signingSecretCipher(
            WebhookProperties properties,
            ResourceLoader resourceLoader
    ) {
        var keyResource = resourceLoader.getResource(properties.signing().encryptionKeyLocation());
        return SigningSecretCipher.from(keyResource);
    }
}
