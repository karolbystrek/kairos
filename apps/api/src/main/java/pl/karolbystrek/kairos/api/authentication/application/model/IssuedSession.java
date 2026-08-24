package pl.karolbystrek.kairos.api.authentication.application.model;

import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.AccessTokenIssuer.IssuedAccessToken;

import java.time.Instant;

public record IssuedSession(
    PanelPrincipal principal,
    IssuedAccessToken accessToken,
    String refreshCredential,
    Instant refreshCookieExpiresAt
) {
}
