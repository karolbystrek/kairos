package pl.karolbystrek.kairos.api.problem;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;

// After the scoped HIGHEST_PRECEDENCE family handlers, before Spring Boot's ProblemDetailsExceptionHandler.
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class ApiExceptionHandler {

    static final String INVALID_REQUEST = "The request is invalid";

    @ExceptionHandler(ProblemException.class)
    ProblemDetail handleProblem(ProblemException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        if (exception.getType() != null) {
            problem.setType(exception.getType());
        }
        return problem;
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidOrderTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        var fieldError = exception.getFieldError();
        var detail = fieldError == null || fieldError.getDefaultMessage() == null
                ? INVALID_REQUEST
                : fieldError.getDefaultMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail handleUnreadableRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
    }
}
