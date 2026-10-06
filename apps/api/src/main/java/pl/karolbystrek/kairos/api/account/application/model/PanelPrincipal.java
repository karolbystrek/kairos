package pl.karolbystrek.kairos.api.account.application.model;

import java.security.Principal;
import java.util.UUID;
public sealed interface PanelPrincipal extends Principal permits StaffPrincipal {
    UUID accountId();
    default String getName() { return accountId().toString(); }
}
