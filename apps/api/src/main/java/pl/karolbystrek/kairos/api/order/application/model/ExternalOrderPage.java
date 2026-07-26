package pl.karolbystrek.kairos.api.order.application.model;

import lombok.NonNull;

import java.util.List;

public record ExternalOrderPage(
        @NonNull List<ExternalOrderView> items,
        String nextCursor
) {

    public ExternalOrderPage {
        items = List.copyOf(items);
    }
}
