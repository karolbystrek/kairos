package pl.karolbystrek.kairos.api.menu.domain;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record MenuDefinition(@NonNull List<String> items) {

    public static final int MAXIMUM_ITEM_LENGTH = 60;
    public static final int MAXIMUM_ITEMS = 100;

    private static final Pattern SINGLE_LINE = Pattern.compile("^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$");

    public MenuDefinition {
        items = List.copyOf(items);
    }

    public static MenuDefinition from(List<String> candidateItems) {
        if (candidateItems == null || candidateItems.isEmpty() || candidateItems.size() > MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("A menu must contain between 1 and 100 items");
        }
        var normalizedItems = new HashSet<String>();
        var items = new ArrayList<String>();
        for (var candidateItem : candidateItems) {
            var item = singleLine(candidateItem);
            if (!normalizedItems.add(item.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Menu item names must be unique");
            }
            items.add(item);
        }
        return new MenuDefinition(items);
    }

    private static String singleLine(String candidate) {
        var value = candidate == null ? "" : candidate.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAXIMUM_ITEM_LENGTH) {
            throw new IllegalArgumentException(
                "Menu item name must contain between 1 and " + MAXIMUM_ITEM_LENGTH + " characters"
            );
        }
        if (!SINGLE_LINE.matcher(value).matches()) {
            throw new IllegalArgumentException("Menu item name must be single-line text without control characters");
        }
        return value;
    }
}
