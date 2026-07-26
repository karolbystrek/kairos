package pl.karolbystrek.kairos.api.integration.webhook.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WebhookSubscriptionLocationAccessId implements Serializable {

    private UUID subscriptionId;
    private UUID locationId;
}
