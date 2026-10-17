package pl.karolbystrek.kairos.api.order.domain;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.ManagedLocationName;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerOrderReviewInvitationTests {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final String REVIEW_URL = "https://g.page/r/example/review";

    private final Location location = Location.create(
            UUID.randomUUID(), ManagedLocationName.from("Cafe"), ZoneOffset.UTC, NOW);

    @Test
    void invitesOnlyCompletedOrdersAfterTheConfiguredDelay() {
        location.configureReviews(REVIEW_URL, NOW);
        var completed = order();
        completed.transitionTo(OrderStatus.READY, NOW);
        completed.transitionTo(OrderStatus.COMPLETED, NOW);
        completed.scheduleReview(NOW.plus(Duration.ofMinutes(1)));
        var canceled = order();
        canceled.transitionTo(OrderStatus.CANCELED, NOW);
        canceled.scheduleReview(NOW.plus(Duration.ofMinutes(1)));

        assertThat(completed.isReviewEligible()).isTrue();
        assertThat(completed.getReviewDueAt()).isEqualTo(Instant.parse("2026-10-10T10:01:00Z"));
        assertThat(canceled.isReviewEligible()).isFalse();
    }

    @Test
    void reconfiguringTheReviewLinkWithdrawsInvitationsScheduledUnderTheOldOne() {
        location.configureReviews(REVIEW_URL, NOW);
        var completed = order();
        completed.transitionTo(OrderStatus.READY, NOW);
        completed.transitionTo(OrderStatus.COMPLETED, NOW);
        completed.scheduleReview(NOW);

        location.configureReviews(null, NOW);
        location.configureReviews(REVIEW_URL, NOW);

        assertThat(completed.isReviewEligible()).isFalse();
    }

    private CustomerOrder order() {
        return CustomerOrder.create(location, 1, NOW.atZone(ZoneOffset.UTC).toLocalDate(), null, List.of(), NOW);
    }
}
