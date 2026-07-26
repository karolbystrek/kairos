package pl.karolbystrek.kairos.api.tenant.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;

@RestControllerAdvice(basePackageClasses = TenantRegistrationController.class)
class TenantRegistrationExceptionHandler {

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail handleConflict(AccountConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
