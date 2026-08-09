package pl.karolbystrek.kairos.api.account.application.exception;

public class SignedInRedemptionException extends RuntimeException {

    public SignedInRedemptionException() {
        super("Sign out before accepting an invitation");
    }
}
