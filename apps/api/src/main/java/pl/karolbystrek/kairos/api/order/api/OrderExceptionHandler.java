package pl.karolbystrek.kairos.api.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;

@RestControllerAdvice(basePackageClasses = {OrderController.class, TrackedOrderController.class})
class OrderExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidOrderTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
