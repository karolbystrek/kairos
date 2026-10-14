package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.network.PublicDestinationPolicy;

@Component
public class WebhookDestinationPolicy extends PublicDestinationPolicy {

    public WebhookDestinationPolicy(WebhookProperties properties) {
        super(
                properties.destinationPolicy() == WebhookProperties.DestinationPolicy.LOCAL_DEVELOPMENT,
                "Webhook destination"
        );
    }
}
