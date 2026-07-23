package pl.karolbystrek.kairos.api.authentication.application.model;

import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.AccessTokenIssuer.IssuedAccessToken;

import java.time.Instant;

public record IssuedSession(
    StaffPrincipal principal,
    IssuedAccessToken accessToken,
    String refreshCredential,
    Instant refreshCookieExpiresAt
) {
}
