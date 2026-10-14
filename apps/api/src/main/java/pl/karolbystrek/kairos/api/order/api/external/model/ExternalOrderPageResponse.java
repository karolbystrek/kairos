package pl.karolbystrek.kairos.api.order.api.external.model;

import pl.karolbystrek.kairos.api.order.api.model.OrderResponse;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderPage;

import java.util.List;

public record ExternalOrderPageResponse(
        List<OrderResponse> items,
        String nextCursor
) {

    public static ExternalOrderPageResponse from(ExternalOrderPage page) {
        return new ExternalOrderPageResponse(
                page.items().stream()
                        .map(OrderResponse::from)
                        .toList(),
                page.nextCursor()
        );
    }
}
