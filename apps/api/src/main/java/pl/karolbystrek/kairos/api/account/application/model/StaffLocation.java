package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;

import java.util.UUID;

public record StaffLocation(
    @NonNull UUID id,
    @NonNull UUID tenantId,
    @NonNull String name
) {
}
