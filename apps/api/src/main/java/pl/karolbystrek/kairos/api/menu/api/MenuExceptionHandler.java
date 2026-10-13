package pl.karolbystrek.kairos.api.menu.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.menu.application.exception.InvalidMenuRequestException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuConflictException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;

import java.net.URI;

@RestControllerAdvice(basePackageClasses = MenuController.class)
class MenuExceptionHandler {

    @ExceptionHandler(MenuNotFoundException.class)
    ProblemDetail handleNotFound(MenuNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidMenuRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidMenuRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MenuConflictException.class)
    ProblemDetail handleConflict(MenuConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setType(URI.create(switch (exception.getReason()) {
            case NAME_CONFLICT -> "urn:kairos:problem:menu-name-conflict";
            case IN_USE -> "urn:kairos:problem:menu-in-use";
        }));
        return problem;
    }

    @ExceptionHandler({StaffAccessDeniedException.class, IntegrationAccessDeniedException.class})
    ProblemDetail handleAccessDenied(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
