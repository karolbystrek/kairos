package pl.karolbystrek.kairos.api.account.application.exception;

public class AccountInvitationNotFoundException extends RuntimeException {

    public AccountInvitationNotFoundException() {
        super("This invitation is not valid");
    }
}
