package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

public final class AuthenticationHttpNames {

    public static final String ACCESS_COOKIE = "__Host-access-token";
    public static final String REFRESH_COOKIE = "__Host-refresh-token";
    public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private AuthenticationHttpNames() {
    }
}
