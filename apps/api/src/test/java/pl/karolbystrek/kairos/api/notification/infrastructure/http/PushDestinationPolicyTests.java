package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushDestinationPolicyTests {

    // Without the allowlist an anonymous caller could aim VAPID-signed POSTs at any public site.
    @Test
    void rejectsHostsThatAreNotBrowserPushServices() {
        var policy = new PushDestinationPolicy(properties());
        for (var endpoint : new String[]{
                "https://example.com/push",
                "https://evil.example/fcm.googleapis.com",
                "https://fcm.googleapis.com.evil.example/push",
                "https://evilpush.apple.com/push"}) {
            assertThatThrownBy(() -> policy.requireAllowedAndResolve(endpoint))
                    .isInstanceOf(InvalidCustomerPushSubscriptionException.class);
        }
    }

    private static CustomerNotificationProperties properties() {
        return new CustomerNotificationProperties(
                new CustomerNotificationProperties.Vapid("public", "private", "mailto:test"),
                new CustomerNotificationProperties.Subscription("encryption", 10, Duration.ofDays(30)),
                new CustomerNotificationProperties.Delivery(Duration.ofMinutes(10), 8, Duration.ofSeconds(5),
                        Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofDays(7), Duration.ofDays(30), 3_072),
                new CustomerNotificationProperties.Worker(100, Duration.ofSeconds(30)),
                CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
        );
    }
}
