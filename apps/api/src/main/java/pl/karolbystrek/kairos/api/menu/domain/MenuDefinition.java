package pl.karolbystrek.kairos.api.menu.domain;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record MenuDefinition(
    @NonNull String name,
    @NonNull String normalizedName,
    @NonNull List<String> items
) {

    public static final int MAXIMUM_NAME_LENGTH = 120;
    public static final int MAXIMUM_NORMALIZED_NAME_LENGTH = 240;
    public static final int MAXIMUM_ITEM_LENGTH = 60;
    public static final int MAXIMUM_ITEMS = 100;

    private static final Pattern SINGLE_LINE = Pattern.compile("^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$");

    public MenuDefinition {
        items = List.copyOf(items);
    }

    public static MenuDefinition from(String candidateName, List<String> candidateItems) {
        var name = singleLine(candidateName, MAXIMUM_NAME_LENGTH, "Menu name");
        if (candidateItems == null || candidateItems.isEmpty() || candidateItems.size() > MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("A menu must contain between 1 and 100 items");
        }
        var normalizedItems = new HashSet<String>();
        var items = new ArrayList<String>();
        for (var candidateItem : candidateItems) {
            var item = singleLine(candidateItem, MAXIMUM_ITEM_LENGTH, "Menu item name");
            if (!normalizedItems.add(item.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Menu item names must be unique");
            }
            items.add(item);
        }
        var normalizedName = name.toLowerCase(Locale.ROOT);
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAXIMUM_NORMALIZED_NAME_LENGTH) {
            throw new IllegalArgumentException("Normalized menu name is too long");
        }
        return new MenuDefinition(name, normalizedName, items);
    }

    private static String singleLine(String candidate, int maximumLength, String subject) {
        var value = candidate == null ? "" : candidate.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maximumLength) {
            throw new IllegalArgumentException(
                subject + " must contain between 1 and " + maximumLength + " characters"
            );
        }
        if (!SINGLE_LINE.matcher(value).matches()) {
            throw new IllegalArgumentException(subject + " must be single-line text without control characters");
        }
        return value;
    }
}
