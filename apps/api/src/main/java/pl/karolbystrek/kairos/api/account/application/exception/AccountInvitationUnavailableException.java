package pl.karolbystrek.kairos.api.account.application.exception;

public class AccountInvitationUnavailableException extends RuntimeException {

    private final Reason reason;

    public AccountInvitationUnavailableException(Reason reason) {
        super(switch (reason) {
            case EXPIRED -> "This invitation has expired";
            case REVOKED -> "This invitation has been revoked";
            case REDEEMED -> "This invitation has already been used";
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
