package pl.karolbystrek.kairos.api.authentication.api.model;

public record CsrfTokenResponse(
    String token,
    String cookieName,
    String headerName
) {
}
