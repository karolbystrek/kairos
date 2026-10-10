package pl.karolbystrek.kairos.api.order.application.model;

import java.time.Instant;

public record ReviewInvitationView(Instant dueAt, String locationName, String googleReviewUrl) {
}
