package pl.karolbystrek.kairos.api.integration.domain;

import lombok.NonNull;

import java.util.Locale;

public final class ManagedIntegrationName {

    private static final int MAXIMUM_CODE_POINTS = 64;
    private static final int MAXIMUM_NORMALIZED_CODE_POINTS = 128;

    private final String value;
    private final String normalizedValue;

    private ManagedIntegrationName(@NonNull String value, @NonNull String normalizedValue) {
        this.value = value;
        this.normalizedValue = normalizedValue;
    }

    public static ManagedIntegrationName from(@NonNull String candidate) {
        var value = candidate.strip();
        var codePointCount = value.codePointCount(0, value.length());
        if (codePointCount == 0 || codePointCount > MAXIMUM_CODE_POINTS) {
            throw new IllegalArgumentException("Name must contain between 1 and 64 characters");
        }
        if (value.codePoints().anyMatch(ManagedIntegrationName::isDisallowedCodePoint)) {
            throw new IllegalArgumentException("Name must be single-line text without control characters");
        }

        var normalizedValue = value.toLowerCase(Locale.ROOT);
        if (normalizedValue.codePointCount(0, normalizedValue.length()) > MAXIMUM_NORMALIZED_CODE_POINTS) {
            throw new IllegalArgumentException("Normalized name is too long");
        }
        return new ManagedIntegrationName(value, normalizedValue);
    }

    public String value() {
        return value;
    }

    public String normalizedValue() {
        return normalizedValue;
    }

    private static boolean isDisallowedCodePoint(int codePoint) {
        var type = Character.getType(codePoint);
        return type == Character.CONTROL
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
