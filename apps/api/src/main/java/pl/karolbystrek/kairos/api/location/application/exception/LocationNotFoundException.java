package pl.karolbystrek.kairos.api.location.application.exception;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException() {
        super("Location was not found");
    }
}
