package pl.karolbystrek.kairos.api.account.application.exception;

public class FinalPlatformOperatorConfirmationRequiredException extends RuntimeException {

    public FinalPlatformOperatorConfirmationRequiredException() {
        super("Disabling the final enabled Platform Operator requires explicit confirmation");
    }
}
