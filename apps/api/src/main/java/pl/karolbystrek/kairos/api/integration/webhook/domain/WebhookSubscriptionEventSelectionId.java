package pl.karolbystrek.kairos.api.integration.webhook.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WebhookSubscriptionEventSelectionId implements Serializable {

    private UUID subscriptionId;
    private OrderEventType eventType;
}
