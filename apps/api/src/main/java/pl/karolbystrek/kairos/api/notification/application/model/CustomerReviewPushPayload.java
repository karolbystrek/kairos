package pl.karolbystrek.kairos.api.notification.application.model;

import java.time.Instant;
import java.util.UUID;

public record CustomerReviewPushPayload(int version, String kind, UUID eventId, UUID trackingReference,
        Instant dueAt, String orderUrl) {
}
