package pl.karolbystrek.kairos.api.integration.webhook.domain;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.util.Arrays;

public enum WebhookEventType {
    ORDER_CREATED("order.created"),
    ORDER_READY("order.ready"),
    ORDER_COMPLETED("order.completed"),
    ORDER_CANCELED("order.canceled");

    private final String cloudEventType;

    WebhookEventType(String cloudEventType) {
        this.cloudEventType = cloudEventType;
    }

    public String cloudEventType() {
        return cloudEventType;
    }

    public static WebhookEventType fromCloudEventType(@NonNull String candidate) {
        return Arrays.stream(values())
                .filter(eventType -> eventType.cloudEventType.equals(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported webhook event type: " + candidate
                ));
    }

    public static WebhookEventType forStatus(@NonNull OrderStatus status) {
        return switch (status) {
            case READY -> ORDER_READY;
            case COMPLETED -> ORDER_COMPLETED;
            case CANCELED -> ORDER_CANCELED;
            case IN_PREPARATION -> throw new IllegalArgumentException(
                    "IN_PREPARATION is represented by the order.created event"
            );
        };
    }
}
