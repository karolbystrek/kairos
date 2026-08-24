package pl.karolbystrek.kairos.api.tenant.application.exception;

public class TenantRegistrationInvitationNotFoundException extends RuntimeException {

    public TenantRegistrationInvitationNotFoundException() {
        super("This invitation is not valid");
    }
}
