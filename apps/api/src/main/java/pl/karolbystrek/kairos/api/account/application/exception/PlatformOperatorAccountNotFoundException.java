package pl.karolbystrek.kairos.api.account.application.exception;

public class PlatformOperatorAccountNotFoundException extends RuntimeException {

    public PlatformOperatorAccountNotFoundException() {
        super("The Platform Operator account was not found");
    }
}
