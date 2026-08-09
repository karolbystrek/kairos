package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;

import java.util.UUID;

public record StaffLocation(
    @NonNull UUID id,
    @NonNull UUID tenantId,
    @NonNull String name,
    @NonNull LocationStatus status
) {

    public boolean isEnabled() {
        return status == LocationStatus.ENABLED;
    }
}
