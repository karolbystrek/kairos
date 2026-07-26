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
@Table(name = "webhook_delivery_signing_versions")
@IdClass(WebhookDeliverySigningVersionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDeliverySigningVersion {

    @Id
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Id
    @Column(name = "signing_secret_version_id", nullable = false)
    private UUID signingSecretVersionId;

    public static WebhookDeliverySigningVersion create(
            @NonNull UUID deliveryId,
            @NonNull UUID signingSecretVersionId
    ) {
        var reference = new WebhookDeliverySigningVersion();
        reference.deliveryId = deliveryId;
        reference.signingSecretVersionId = signingSecretVersionId;
        return reference;
    }
}
