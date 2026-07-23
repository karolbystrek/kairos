package pl.karolbystrek.kairos.api.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;

@RestControllerAdvice(basePackageClasses = AccountController.class)
class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(AccountNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail handleConflict(AccountConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidAccountRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
