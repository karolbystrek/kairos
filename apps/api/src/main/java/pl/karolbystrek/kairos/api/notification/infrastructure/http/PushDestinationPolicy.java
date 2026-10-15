package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.network.InvalidDestinationException;
import pl.karolbystrek.kairos.api.network.PublicDestinationPolicy;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class PushDestinationPolicy extends PublicDestinationPolicy {

    public PushDestinationPolicy(CustomerNotificationProperties properties) {
        super(
                properties.destinationPolicy() == CustomerNotificationProperties.DestinationPolicy.LOCAL_DEVELOPMENT,
                "Push endpoint"
        );
    }

    @Override
    public AllowedDestination requireAllowedAndResolve(String candidate) {
        try {
            return super.requireAllowedAndResolve(candidate);
        } catch (InvalidDestinationException exception) {
            throw new InvalidCustomerPushSubscriptionException(exception.getMessage(), exception);
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
