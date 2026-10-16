package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.network.InvalidDestinationException;
import pl.karolbystrek.kairos.api.network.PublicDestinationPolicy;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

@Component
public class PushDestinationPolicy extends PublicDestinationPolicy {

    // Anonymous callers choose the endpoint, so only real browser push services may receive our POSTs.
    private static final List<String> PUSH_SERVICE_DOMAINS = List.of(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "push.apple.com",
            "notify.windows.com"
    );

    private final boolean localDevelopment;

    public PushDestinationPolicy(CustomerNotificationProperties properties) {
        super(isLocal(properties), "Push endpoint");
        this.localDevelopment = isLocal(properties);
    }

    private static boolean isLocal(CustomerNotificationProperties properties) {
        return properties.destinationPolicy() == CustomerNotificationProperties.DestinationPolicy.LOCAL_DEVELOPMENT;
    }

    @Override
    public AllowedDestination requireAllowedAndResolve(String candidate) {
        try {
            if (!localDevelopment) {
                requirePushServiceHost(candidate);
            }
            return super.requireAllowedAndResolve(candidate);
        } catch (InvalidDestinationException exception) {
            throw new InvalidCustomerPushSubscriptionException(exception.getMessage(), exception);
        }
    }

    private static void requirePushServiceHost(String candidate) {
        String host;
        try {
            host = new URI(candidate.strip()).getHost();
        } catch (URISyntaxException | NullPointerException ignored) {
            return; // The base policy reports malformed or missing URLs.
        }
        if (host == null) {
            return;
        }
        var lower = host.toLowerCase(Locale.ROOT);
        if (PUSH_SERVICE_DOMAINS.stream().noneMatch(domain ->
                lower.equals(domain) || lower.endsWith("." + domain))) {
            throw new InvalidDestinationException("Push endpoint must use a browser push service host");
        }
    }

    public static String origin(URI uri) {
        try {
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Validated Push endpoint has an invalid origin", exception);
        }
    }
}
