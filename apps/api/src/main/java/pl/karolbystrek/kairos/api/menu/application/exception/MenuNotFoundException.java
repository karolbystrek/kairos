package pl.karolbystrek.kairos.api.menu.application.exception;

public class MenuNotFoundException extends RuntimeException {

    public MenuNotFoundException(String message) {
        super(message);
    }
}
