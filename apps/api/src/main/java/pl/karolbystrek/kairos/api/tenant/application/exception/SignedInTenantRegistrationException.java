package pl.karolbystrek.kairos.api.tenant.application.exception;

public class SignedInTenantRegistrationException extends RuntimeException {

    public SignedInTenantRegistrationException() {
        super("Sign out before registering a tenant");
    }
}
