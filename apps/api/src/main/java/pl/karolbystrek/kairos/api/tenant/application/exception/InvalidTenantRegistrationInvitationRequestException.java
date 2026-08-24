package pl.karolbystrek.kairos.api.tenant.application.exception;

public class InvalidTenantRegistrationInvitationRequestException extends RuntimeException {

    public InvalidTenantRegistrationInvitationRequestException(String message) {
        super(message);
    }
}
