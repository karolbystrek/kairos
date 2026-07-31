package pl.karolbystrek.kairos.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("staging")
public class PrivateStagingConfiguration {

    @Bean
    PrivateStagingConfigurationContract privateStagingConfigurationContract(
            Environment environment,
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
}

final class PrivateStagingConfigurationContract {

    private static final Set<String> DEVELOPMENT_USERNAMES = Set.of(
            "default",
            "kairos",
            "kairos-user",
            "postgres",
            "redis"
    );
    private static final Set<String> DEVELOPMENT_PASSWORDS = Set.of(
            "changeme",
            "kairos-password",
            "password",
            "postgres",
            "redis"
    );

    PrivateStagingConfigurationContract(
            Environment environment,
            ApplicationOriginsProperties origins,
            AuthenticationProperties authenticationProperties,
            WebhookProperties webhookProperties,
            CustomerNotificationProperties notificationProperties
    ) {
        var customerOrigin = requireHttpsOrigin(
                "customer origin",
                origins.customer()
        );
        var panelOrigin = requireHttpsOrigin(
                "panel origin",
                origins.panel()
        );
        var apiOrigin = requireHttpsOrigin(
                "API origin",
                origins.api()
        );
        requireDistinctOrigins(customerOrigin, panelOrigin, apiOrigin);

        var jwt = authenticationProperties.jwt();
        if (!apiOrigin.equals(requireHttpsOrigin("JWT issuer", jwt.issuer()))) {
            throw invalid("JWT issuer must equal the private staging API origin");
        }

        if (webhookProperties.destinationPolicy()
                != WebhookProperties.DestinationPolicy.PUBLIC_HTTPS) {
            throw invalid("webhook destinations must use the PUBLIC_HTTPS policy");
        }
        if (notificationProperties.destinationPolicy()
                != CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS) {
            throw invalid("Customer Push destinations must use the PUBLIC_HTTPS policy");
        }

        requireVapidContact(notificationProperties.vapid().subject());
        requireExternalKeyLocation("JWT public key", jwt.publicKeyLocation());
        requireExternalKeyLocation("JWT private key", jwt.privateKeyLocation());
        requireExternalKeyLocation(
                "webhook encryption key",
                webhookProperties.signing().encryptionKeyLocation()
        );
        requireExternalKeyLocation(
                "VAPID public key",
                notificationProperties.vapid().publicKeyLocation()
        );
        requireExternalKeyLocation(
                "VAPID private key",
                notificationProperties.vapid().privateKeyLocation()
        );
        requireExternalKeyLocation(
                "push-subscription encryption key",
                notificationProperties.subscription().encryptionKeyLocation()
        );

        requirePostgresqlUrl(environment.getRequiredProperty("spring.datasource.url"));
        requireCredential(
                "PostgreSQL username",
                environment.getRequiredProperty("spring.datasource.username"),
                DEVELOPMENT_USERNAMES,
                false
        );
        requireCredential(
                "PostgreSQL password",
                environment.getRequiredProperty("spring.datasource.password"),
                DEVELOPMENT_PASSWORDS,
                true
        );
        requireText(
                "Redis host",
                environment.getRequiredProperty("spring.data.redis.host")
        );
        var redisPort = environment.getRequiredProperty("spring.data.redis.port", Integer.class);
        if (redisPort == null || redisPort < 1 || redisPort > 65_535) {
            throw invalid("Redis port must be between 1 and 65535");
        }
        requireCredential(
                "Redis username",
                environment.getRequiredProperty("spring.data.redis.username"),
                DEVELOPMENT_USERNAMES,
                false
        );
        requireCredential(
                "Redis password",
                environment.getRequiredProperty("spring.data.redis.password"),
                DEVELOPMENT_PASSWORDS,
                true
        );
    }

    private static URI requireHttpsOrigin(String name, String value) {
        requireText(name, value);
        try {
            var origin = URI.create(value);
            if (!"https".equalsIgnoreCase(origin.getScheme())
                    || !StringUtils.hasText(origin.getHost())
                    || origin.getUserInfo() != null
                    || StringUtils.hasText(origin.getPath())
                    || origin.getQuery() != null
                    || origin.getFragment() != null
                    || isReservedHost(origin.getHost())) {
                throw invalid(name + " must be a non-placeholder HTTPS origin without a path");
            }
            return origin;
        }
        catch (IllegalArgumentException exception) {
            throw invalid(name + " must be a valid HTTPS origin", exception);
        }
    }

    private static void requireDistinctOrigins(URI... origins) {
        var distinctOrigins = new HashSet<URI>();
        Collections.addAll(distinctOrigins, origins);
        if (distinctOrigins.size() != origins.length) {
            throw invalid("customer, panel, and API origins must be distinct");
        }
    }

    private static void requireVapidContact(String value) {
        requireText("VAPID contact subject", value);
        try {
            var subject = URI.create(value);
            var address = subject.getSchemeSpecificPart();
            var separator = address == null ? -1 : address.lastIndexOf('@');
            if (!"mailto".equalsIgnoreCase(subject.getScheme())
                    || subject.getQuery() != null
                    || subject.getFragment() != null
                    || separator < 1
                    || separator == address.length() - 1
                    || address.indexOf(' ') >= 0
                    || isReservedHost(address.substring(separator + 1))
                    || "your-domain".equalsIgnoreCase(address.substring(separator + 1))) {
                throw invalid("VAPID contact subject must be a real mailto address");
            }
        }
        catch (IllegalArgumentException exception) {
            throw invalid("VAPID contact subject must be a valid mailto URI", exception);
        }
    }

    private static void requireExternalKeyLocation(String name, String value) {
        requireText(name + " location", value);
        try {
            var location = URI.create(value);
            if (!"file".equalsIgnoreCase(location.getScheme())
                    || !StringUtils.hasText(location.getPath())
                    || !location.getPath().startsWith("/")) {
                throw invalid(name + " location must be an absolute file URI");
            }
        }
        catch (IllegalArgumentException exception) {
            throw invalid(name + " location must be a valid absolute file URI", exception);
        }
    }

    private static void requirePostgresqlUrl(String value) {
        requireText("PostgreSQL URL", value);
        var normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("jdbc:postgresql://")
                || normalized.endsWith("/kairos-db")
                || normalized.contains("localhost")
                || normalized.contains("127.0.0.1")) {
            throw invalid("PostgreSQL URL must identify the private staging database");
        }
    }

    private static void requireCredential(
            String name,
            String value,
            Set<String> developmentValues,
            boolean requireLongValue
    ) {
        requireText(name, value);
        var normalized = value.toLowerCase(Locale.ROOT);
        if (developmentValues.contains(normalized)
                || normalized.contains("changeme")
                || normalized.contains("example")
                || normalized.contains("placeholder")
                || normalized.contains("${")
                || (requireLongValue && value.length() < 16)) {
            throw invalid(name + " must not use a development or placeholder value");
        }
    }

    private static void requireText(String name, String value) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw invalid(name + " is required");
        }
    }

    private static boolean isReservedHost(String value) {
        var host = value.toLowerCase(Locale.ROOT);
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.equals("example.com")
                || host.endsWith(".example.com")
                || host.endsWith(".example")
                || host.endsWith(".invalid")
                || host.endsWith(".test");
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid private staging configuration: " + message);
    }

    private static IllegalStateException invalid(String message, Exception cause) {
        return new IllegalStateException(
                "Invalid private staging configuration: " + message,
                cause
        );
    }
}
