package pl.karolbystrek.kairos.api.account.application.exception;

public class StaffAccessDeniedException extends RuntimeException {

    public StaffAccessDeniedException(String message) {
        super(message);
    }
}
