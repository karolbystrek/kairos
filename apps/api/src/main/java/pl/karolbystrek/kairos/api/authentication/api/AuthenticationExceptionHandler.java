package pl.karolbystrek.kairos.api.authentication.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AuthenticationController.class)
class AuthenticationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var fields = new LinkedHashMap<String, String>();
        for (var error : exception.getBindingResult().getFieldErrors()) {
            var field = error.getField().equals("passwordConfirmed") ? "passwordConfirmation" : error.getField();
            fields.putIfAbsent(field, error.getDefaultMessage());
        }
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the highlighted fields.");
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @ExceptionHandler(RegistrationValidationException.class)
    ProblemDetail handleRegistrationValidation(RegistrationValidationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), "Check the highlighted field.");
        problem.setProperty("fieldErrors", Map.of(exception.getField(), exception.getReason()));
        return problem;
    }

    @ExceptionHandler(InvalidLoginException.class)
    ProblemDetail handleInvalidAuthentication(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({AuthenticationException.class})
    ProblemDetail handleInvalidIdentity(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid authentication");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, AccountConflictException.class})
    ProblemDetail handleConcurrentRegistration(RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registration conflicts with an existing account.");
        problem.setProperty("fieldErrors", Map.of("email", "An account with this email already exists. Sign in instead."));
        return problem;
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
