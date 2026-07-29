package pl.karolbystrek.kairos.api.notification.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.PushSubscriptionCipher;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyLoader;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerNotificationProperties.class)
public class CustomerNotificationConfiguration {

    @Bean
    PushSubscriptionCipher pushSubscriptionCipher(
            CustomerNotificationProperties properties,
            ResourceLoader resourceLoader
    ) {
        var resource = resourceLoader.getResource(
                properties.subscription().encryptionKeyLocation()
        );
        return PushSubscriptionCipher.from(resource);
    }

    @Bean
    VapidKeyMaterial vapidKeyMaterial(
            CustomerNotificationProperties properties,
            ResourceLoader resourceLoader
    ) {
        var vapid = properties.vapid();
        return VapidKeyLoader.load(
                resourceLoader.getResource(vapid.publicKeyLocation()),
                resourceLoader.getResource(vapid.privateKeyLocation())
        );
    }
}
