package pl.karolbystrek.kairos.api.authentication.api.model;

public record CsrfTokenResponse(
    String cookieName,
    String headerName
) {
}
