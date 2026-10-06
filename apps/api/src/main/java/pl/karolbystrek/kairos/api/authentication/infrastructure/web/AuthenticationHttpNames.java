package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

public final class AuthenticationHttpNames {

    public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private AuthenticationHttpNames() {
    }
}
