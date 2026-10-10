package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "kairos.review-invitations.delay=1m")
@Import(MutableTestClockConfiguration.class)
class ReviewInvitationTimingIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired
    private OrderService orders;
    @Autowired
    private LocationService locations;
    @Autowired
    private MutableTestClock clock;

    @Test
    void usesTheConfiguredDelayAndNeverInvitesCanceledOrders() {
        clock.setInstant(Instant.parse("2026-10-10T10:00:00Z"));
        var tenant = new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        locations.updateReviewLink(tenant.administrator(), tenant.firstLocationId(), "https://g.page/r/example/review");
        var order = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.READY);
        orders.updateStatus(tenant.administrator(), order.id(), OrderStatus.COMPLETED);
        assertThat(orders.findTrackedOrder(order.trackingReference()).reviewInvitation().dueAt())
                .isEqualTo(Instant.parse("2026-10-10T10:01:00Z"));
        var canceled = orders.createOrder(tenant.administrator(), tenant.firstLocationId(), null);
        orders.updateStatus(tenant.administrator(), canceled.id(), OrderStatus.CANCELED);
        assertThat(orders.findTrackedOrder(canceled.trackingReference()).reviewInvitation()).isNull();
        locations.updateReviewLink(tenant.administrator(), tenant.firstLocationId(), null);
        locations.updateReviewLink(tenant.administrator(), tenant.firstLocationId(), "https://g.page/r/example/review");
        assertThat(orders.findTrackedOrder(order.trackingReference()).reviewInvitation()).isNull();
    }
}
