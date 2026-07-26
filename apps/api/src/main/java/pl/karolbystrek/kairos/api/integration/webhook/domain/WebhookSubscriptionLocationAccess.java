package pl.karolbystrek.kairos.api.integration.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

@Entity
@Table(name = "webhook_subscription_location_access")
@IdClass(WebhookSubscriptionLocationAccessId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookSubscriptionLocationAccess {

    @Id
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public static WebhookSubscriptionLocationAccess create(
            @NonNull UUID subscriptionId,
            @NonNull UUID locationId,
            @NonNull UUID tenantId
    ) {
        var access = new WebhookSubscriptionLocationAccess();
        access.subscriptionId = subscriptionId;
        access.locationId = locationId;
        access.tenantId = tenantId;
        return access;
    }
}
