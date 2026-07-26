package pl.karolbystrek.kairos.api.integration.domain;

import lombok.NonNull;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum ApiKeyScope {
    ORDERS_READ("orders:read"),
    ORDERS_WRITE("orders:write");

    private final String externalValue;

    ApiKeyScope(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }

    public boolean grants(@NonNull ApiKeyScope required) {
        return this == required || this == ORDERS_WRITE && required == ORDERS_READ;
    }

    public static ApiKeyScope fromExternalValue(@NonNull String value) {
        var normalized = value.strip().toLowerCase(Locale.ROOT);
        for (var scope : values()) {
            if (scope.externalValue.equals(normalized)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unsupported API Key scope: " + value);
    }

    public static Set<ApiKeyScope> normalize(@NonNull Set<ApiKeyScope> scopes) {
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("At least one API Key scope is required");
        }

        var normalized = EnumSet.copyOf(scopes);
        if (normalized.contains(ORDERS_WRITE)) {
            normalized.add(ORDERS_READ);
        }
        return Set.copyOf(normalized);
    }
}
