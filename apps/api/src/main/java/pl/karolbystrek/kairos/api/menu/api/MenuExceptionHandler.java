package pl.karolbystrek.kairos.api.menu.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.menu.application.exception.InvalidMenuRequestException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;

@RestControllerAdvice(basePackageClasses = LocationMenuController.class)
class MenuExceptionHandler {

    @ExceptionHandler(MenuNotFoundException.class)
    ProblemDetail handleNotFound(MenuNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidMenuRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidMenuRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({StaffAccessDeniedException.class, IntegrationAccessDeniedException.class})
    ProblemDetail handleAccessDenied(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
