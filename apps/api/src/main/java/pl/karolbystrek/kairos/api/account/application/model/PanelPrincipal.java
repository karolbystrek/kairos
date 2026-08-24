package pl.karolbystrek.kairos.api.account.application.model;

import pl.karolbystrek.kairos.api.account.domain.AccountKind;

import java.security.Principal;
import java.util.UUID;

public sealed interface PanelPrincipal extends Principal permits StaffPrincipal, PlatformOperatorPrincipal {

    UUID accountId();

    AccountKind kind();

    @Override
    default String getName() {
        return accountId().toString();
    }
}
