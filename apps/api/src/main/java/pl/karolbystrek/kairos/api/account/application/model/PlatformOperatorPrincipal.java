package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;

import java.util.UUID;

public record PlatformOperatorPrincipal(
    @NonNull UUID accountId
) implements PanelPrincipal {

    @Override
    public AccountKind kind() {
        return AccountKind.PLATFORM_OPERATOR;
    }
}
