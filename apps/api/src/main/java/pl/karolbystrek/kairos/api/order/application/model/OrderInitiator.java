package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.InitiatorType;

import java.util.UUID;

public record OrderInitiator(
        @NonNull InitiatorType type,
        UUID initiatorId,
        UUID apiKeyId
) {

    public OrderInitiator {
        if (type == InitiatorType.SYSTEM
                && (initiatorId != null || apiKeyId != null)) {
            throw new IllegalArgumentException("System actions cannot carry an initiator identity");
        }
        if (type == InitiatorType.USER
                && (initiatorId == null || apiKeyId != null)) {
            throw new IllegalArgumentException("User actions cannot carry API Key identity");
        }
        if (type == InitiatorType.INTEGRATION
                && (initiatorId == null || apiKeyId == null)) {
            throw new IllegalArgumentException("Integration actions require API Key identity");
        }
    }

    public static OrderInitiator user(@NonNull UUID accountId) {
        return new OrderInitiator(InitiatorType.USER, accountId, null);
    }

    public static OrderInitiator integration(
            @NonNull UUID integrationId,
            @NonNull UUID apiKeyId
    ) {
        return new OrderInitiator(
                InitiatorType.INTEGRATION,
                integrationId,
                apiKeyId
        );
    }

    public static OrderInitiator system() {
        return new OrderInitiator(InitiatorType.SYSTEM, null, null);
    }
}
