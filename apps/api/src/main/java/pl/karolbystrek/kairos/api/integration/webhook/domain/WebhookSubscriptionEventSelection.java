package pl.karolbystrek.kairos.api.integration.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;

import java.util.UUID;

@Entity
@Table(name = "webhook_subscription_event_types")
@IdClass(WebhookSubscriptionEventSelectionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookSubscriptionEventSelection {

    @Id
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private OrderEventType eventType;

    public static WebhookSubscriptionEventSelection create(
            @NonNull UUID subscriptionId,
            @NonNull OrderEventType eventType
    ) {
        var selection = new WebhookSubscriptionEventSelection();
        selection.subscriptionId = subscriptionId;
        selection.eventType = eventType;
        return selection;
    }
}
