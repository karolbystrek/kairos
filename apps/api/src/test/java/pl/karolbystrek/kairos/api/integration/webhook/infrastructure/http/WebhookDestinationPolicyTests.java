package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDestinationPolicyTests {

    @Test
    void productionRequiresHttpsAndRejectsPrivateOrSpecialAddresses() {
        var policy = new WebhookDestinationPolicy(properties(
                WebhookProperties.DestinationPolicy.PUBLIC_HTTPS
        ));

        assertThatThrownBy(() -> policy.requireAllowed("http://8.8.8.8/events"))
                .isInstanceOf(InvalidWebhookDestinationException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://127.0.0.1/events"))
                .isInstanceOf(InvalidWebhookDestinationException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://169.254.169.254/latest"))
                .isInstanceOf(InvalidWebhookDestinationException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://[::1]/events"))
                .isInstanceOf(InvalidWebhookDestinationException.class);
    }

    @Test
    void productionAllowsPublicHttpsAndReturnsDefensiveDnsSnapshots() {
        var policy = new WebhookDestinationPolicy(properties(
                WebhookProperties.DestinationPolicy.PUBLIC_HTTPS
        ));

        var allowed = policy.requireAllowedAndResolve("https://8.8.8.8/events");
        var firstSnapshot = allowed.addresses();
        firstSnapshot[0] = null;

        assertThat(allowed.uri().toASCIIString()).isEqualTo("https://8.8.8.8/events");
        assertThat(allowed.addresses()).doesNotContainNull();
    }

    @Test
    void localDevelopmentExplicitlyAllowsHttpAndPrivateAddresses() {
        var policy = new WebhookDestinationPolicy(properties(
                WebhookProperties.DestinationPolicy.LOCAL_DEVELOPMENT
        ));

        assertThat(policy.requireAllowed("http://127.0.0.1:9080/events").toString())
                .isEqualTo("http://127.0.0.1:9080/events");
    }

    private static WebhookProperties properties(
            WebhookProperties.DestinationPolicy destinationPolicy
    ) {
        return new WebhookProperties(
                new WebhookProperties.Signing("unused", Duration.ofHours(24)),
                new WebhookProperties.Delivery(Duration.ofSeconds(10), 16_384),
                new WebhookProperties.Worker(10, Duration.ofSeconds(30)),
                destinationPolicy
        );
    }
}
