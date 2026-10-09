package pl.karolbystrek.kairos.api.location.domain;

import lombok.NonNull;

import java.util.Locale;

public record ManagedLocationName(@NonNull String value, @NonNull String normalizedValue) {

    public static final int MAXIMUM_LENGTH = 120;
    public static final int MAXIMUM_NORMALIZED_LENGTH = 240;

    public static ManagedLocationName from(String candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Location name is required");
        }
        var value = candidate.strip();
        if (value.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException(
                "Location name must contain between 1 and 120 characters"
            );
        }
        var normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.length() > MAXIMUM_NORMALIZED_LENGTH) {
            throw new IllegalArgumentException("Normalized location name is too long");
        }
        return new ManagedLocationName(value, normalized);
    }
}
