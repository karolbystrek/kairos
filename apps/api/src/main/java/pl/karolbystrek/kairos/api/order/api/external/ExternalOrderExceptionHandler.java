package pl.karolbystrek.kairos.api.order.api.external;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.order.application.exception.ExternalOrderConflictException;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ExternalOrderController.class)
class ExternalOrderExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        var fieldError = exception.getFieldError();
        var detail = fieldError == null || fieldError.getDefaultMessage() == null
                ? "The order request is invalid"
                : fieldError.getDefaultMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(InvalidOrderRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidOrderRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ExternalOrderConflictException.class, InvalidOrderTransitionException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IntegrationAccessDeniedException.class)
    ProblemDetail handleAccessDenied(IntegrationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
