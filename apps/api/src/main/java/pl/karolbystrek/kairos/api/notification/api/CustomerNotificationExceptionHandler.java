package pl.karolbystrek.kairos.api.notification.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.notification.application.exception.CustomerPushEnrollmentLimitException;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = CustomerNotificationController.class)
class CustomerNotificationExceptionHandler {

    @ExceptionHandler(CustomerPushEnrollmentLimitException.class)
    ProblemDetail enrollmentLimit(CustomerPushEnrollmentLimitException exception) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Notification enrollment limit reached");
        problem.setProperty("code", "CUSTOMER_PUSH_ENROLLMENT_LIMIT");
        problem.setProperty("trackingReference", exception.getTrackingReference());
        return problem;
    }

    @ExceptionHandler({
            InvalidCustomerPushSubscriptionException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail invalidRequest(Exception exception) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception instanceof InvalidCustomerPushSubscriptionException
                        ? exception.getMessage()
                        : "Customer notification request is invalid"
        );
        problem.setTitle("Invalid customer notification request");
        problem.setProperty("code", "INVALID_CUSTOMER_PUSH_SUBSCRIPTION");
        return problem;
    }
}
