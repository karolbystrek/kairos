package pl.karolbystrek.kairos.api.order.api.external.model;

import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderPage;

import java.util.List;

public record ExternalOrderPageResponse(
        List<ExternalOrderResponse> items,
        String nextCursor
) {

    public static ExternalOrderPageResponse from(ExternalOrderPage page) {
        return new ExternalOrderPageResponse(
                page.items().stream()
                        .map(ExternalOrderResponse::from)
                        .toList(),
                page.nextCursor()
        );
    }
}
