package pl.karolbystrek.kairos.api.account.application.model;

import lombok.NonNull;

public record CreatedAccountInvitation(
    @NonNull AccountInvitationView invitation,
    @NonNull String token
) {
}
