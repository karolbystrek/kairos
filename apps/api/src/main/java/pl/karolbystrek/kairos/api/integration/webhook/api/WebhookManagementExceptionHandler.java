package pl.karolbystrek.kairos.api.integration.webhook.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = {
        WebhookSubscriptionController.class,
        WebhookSigningSecretController.class
})
class WebhookManagementExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
        var fieldError = exception.getFieldError();
        var detail = fieldError == null || fieldError.getDefaultMessage() == null
                ? "The webhook request is invalid"
                : fieldError.getDefaultMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The webhook request is invalid"
        );
    }

    @ExceptionHandler(InvalidIntegrationRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidIntegrationRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IntegrationNotFoundException.class)
    ProblemDetail handleNotFound(IntegrationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IntegrationConflictException.class)
    ProblemDetail handleConflict(IntegrationConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({IntegrationAccessDeniedException.class, StaffAccessDeniedException.class})
    ProblemDetail handleAccessDenied(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
