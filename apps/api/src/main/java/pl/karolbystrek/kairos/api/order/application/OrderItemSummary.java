package pl.karolbystrek.kairos.api.order.application;

import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.model.OrderItemSelection;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

final class OrderItemSummary {

    private OrderItemSummary() {}

    // Items follow the menu order so equal selections always produce the same label.
    static String describe(List<OrderItemSelection> selections, List<String> menuItems) {
        if (menuItems.isEmpty()) {
            throw new InvalidOrderRequestException("This location has no menu to choose items from");
        }
        var quantities = new HashMap<String, Integer>();
        for (var selection : selections) {
            if (!menuItems.contains(selection.name())) {
                throw new InvalidOrderRequestException(
                        "The menu has changed; reload it and choose the items again"
                );
            }
            if (quantities.putIfAbsent(selection.name(), selection.quantity()) != null) {
                throw new InvalidOrderRequestException("Each menu item can be chosen only once");
            }
        }
        return menuItems.stream()
                .filter(quantities::containsKey)
                .map(item -> quantities.get(item) == 1 ? item : quantities.get(item) + "× " + item)
                .collect(Collectors.joining(", "));
    }
}
