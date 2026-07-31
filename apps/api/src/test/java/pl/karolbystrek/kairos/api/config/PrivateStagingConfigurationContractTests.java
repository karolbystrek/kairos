package pl.karolbystrek.kairos.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivateStagingConfigurationContractTests {

    @Test
    void acceptsACompletePrivateStagingContract() {
        assertThatCode(() -> validContract(environment())).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonHttpsPlaceholderAndRepeatedOrigins() {
        assertThatThrownBy(() -> contract(
                environment(),
                new ApplicationOriginsProperties(
                        "http://customer.staging.kairos.example.org",
                        "https://panel.staging.kairos.example.org",
                        "https://api.staging.kairos.example.org"
                ),
                authenticationProperties("https://api.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("customer origin");

        assertThatThrownBy(() -> contract(
                environment(),
                new ApplicationOriginsProperties(
                        "https://customer.staging.kairos.example.org",
                        "https://panel.staging.kairos.example.org",
                        "https://customer.staging.kairos.example.org"
                ),
                authenticationProperties("https://customer.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("origins must be distinct");
    }

    @Test
    void rejectsAJwtIssuerThatDoesNotMatchTheApiOrigin() {
        assertThatThrownBy(() -> contract(
                environment(),
                origins(),
                authenticationProperties("https://another.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("JWT issuer");
    }

    @Test
    void rejectsRelaxedDeliveryPolicies() {
        assertThatThrownBy(() -> contract(
                environment(),
                origins(),
                authenticationProperties("https://api.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.LOCAL_DEVELOPMENT),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("webhook destinations");

        assertThatThrownBy(() -> contract(
                environment(),
                origins(),
                authenticationProperties("https://api.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.LOCAL_DEVELOPMENT
                )
        )).hasMessageContaining("Customer Push destinations");
    }

    @Test
    void rejectsLocalVapidIdentityAndPackagedKeyMaterial() {
        assertThatThrownBy(() -> contract(
                environment(),
                origins(),
                authenticationProperties("https://api.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:kairos@localhost",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("VAPID contact subject");

        assertThatThrownBy(() -> contract(
                environment(),
                origins(),
                authenticationProperties(
                        "https://api.staging.kairos.example.org",
                        "classpath:keys/jwt-public.pem"
                ),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        )).hasMessageContaining("JWT public key location");
    }

    @Test
    void rejectsDevelopmentDatabaseAndRedisCredentials() {
        var developmentDatabase = environment()
                .withProperty("spring.datasource.username", "kairos-user");
        assertThatThrownBy(() -> validContract(developmentDatabase))
                .hasMessageContaining("PostgreSQL username");

        var shortRedisPassword = environment()
                .withProperty("spring.data.redis.password", "too-short");
        assertThatThrownBy(() -> validContract(shortRedisPassword))
                .hasMessageContaining("Redis password");
    }

    private static PrivateStagingConfigurationContract validContract(MockEnvironment environment) {
        return contract(
                environment,
                origins(),
                authenticationProperties("https://api.staging.kairos.example.org"),
                webhookProperties(WebhookProperties.DestinationPolicy.PUBLIC_HTTPS),
                notificationProperties(
                        "mailto:admin@kairos.example.org",
                        CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
                )
        );
    }

    private static PrivateStagingConfigurationContract contract(
            MockEnvironment environment,
            ApplicationOriginsProperties origins,
            AuthenticationProperties authenticationProperties,
            WebhookProperties webhookProperties,
            CustomerNotificationProperties notificationProperties
    ) {
        return new PrivateStagingConfigurationContract(
                environment,
                origins,
                authenticationProperties,
                webhookProperties,
                notificationProperties
        );
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://postgres:5432/kairos_staging"
                )
                .withProperty("spring.datasource.username", "kairos_staging_app")
                .withProperty(
                        "spring.datasource.password",
                        "staging-postgres-secret"
                )
                .withProperty("spring.data.redis.host", "redis")
                .withProperty("spring.data.redis.port", "6379")
                .withProperty("spring.data.redis.username", "kairos_staging_cache")
                .withProperty(
                        "spring.data.redis.password",
                        "staging-redis-secret"
                );
    }

    private static ApplicationOriginsProperties origins() {
        return new ApplicationOriginsProperties(
                "https://customer.staging.kairos.example.org",
                "https://panel.staging.kairos.example.org",
                "https://api.staging.kairos.example.org"
        );
    }

    private static AuthenticationProperties authenticationProperties(String issuer) {
        return authenticationProperties(issuer, "file:/run/secrets/jwt-public.pem");
    }

    private static AuthenticationProperties authenticationProperties(
            String issuer,
            String publicKeyLocation
    ) {
        return new AuthenticationProperties(
                new AuthenticationProperties.Jwt(
                        issuer,
                        "kairos-panel",
                        Duration.ofMinutes(5),
                        publicKeyLocation,
                        "file:/run/secrets/jwt-private.pem"
                ),
                new AuthenticationProperties.Refresh(Duration.ofDays(7), Duration.ofDays(30)),
                new AuthenticationProperties.Password(12)
        );
    }

    private static WebhookProperties webhookProperties(
            WebhookProperties.DestinationPolicy destinationPolicy
    ) {
        return new WebhookProperties(
                new WebhookProperties.Signing(
                        "file:/run/secrets/webhook-encryption.bin",
                        Duration.ofHours(24)
                ),
                new WebhookProperties.Delivery(Duration.ofSeconds(10), 16_384),
                new WebhookProperties.Worker(50, Duration.ofSeconds(30)),
                destinationPolicy
        );
    }

    private static CustomerNotificationProperties notificationProperties(
            String vapidSubject,
            CustomerNotificationProperties.DestinationPolicy destinationPolicy
    ) {
        return new CustomerNotificationProperties(
                new CustomerNotificationProperties.Vapid(
                        "file:/run/secrets/vapid-public.pem",
                        "file:/run/secrets/vapid-private.pem",
                        vapidSubject
                ),
                new CustomerNotificationProperties.Subscription(
                        "file:/run/secrets/push-subscription-encryption.bin",
                        10,
                        Duration.ofDays(30)
                ),
                new CustomerNotificationProperties.Delivery(
                        Duration.ofMinutes(10),
                        8,
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(2),
                        Duration.ofSeconds(10),
                        Duration.ofDays(7),
                        Duration.ofDays(30),
                        3_072
                ),
                new CustomerNotificationProperties.Worker(50, Duration.ofSeconds(30)),
                destinationPolicy
        );
    }
}
