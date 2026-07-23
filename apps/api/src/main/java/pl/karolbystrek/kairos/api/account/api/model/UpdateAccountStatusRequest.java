package pl.karolbystrek.kairos.api.account.api.model;

import jakarta.validation.constraints.NotNull;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;

public record UpdateAccountStatusRequest(
    @NotNull(message = "Account status is required")
    AccountStatus status
) {
}
