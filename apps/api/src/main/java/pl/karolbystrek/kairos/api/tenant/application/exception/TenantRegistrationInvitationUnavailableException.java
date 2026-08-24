package pl.karolbystrek.kairos.api.tenant.application.exception;

public class TenantRegistrationInvitationUnavailableException extends RuntimeException {

    private final Reason reason;

    public TenantRegistrationInvitationUnavailableException(Reason reason) {
        super(switch (reason) {
            case EXPIRED -> "This invitation has expired";
            case REVOKED -> "This invitation was revoked";
            case REDEEMED -> "This invitation was already used";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EXPIRED,
        REVOKED,
        REDEEMED
    }
}
