package pl.karolbystrek.kairos.api.location.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.location.application.exception.InvalidLocationRequestException;
import pl.karolbystrek.kairos.api.location.application.exception.LocationConflictException;
import pl.karolbystrek.kairos.api.location.application.exception.LocationNotFoundException;

@RestControllerAdvice(basePackageClasses = LocationController.class)
class LocationExceptionHandler {

    @ExceptionHandler(LocationNotFoundException.class)
    ProblemDetail handleNotFound(LocationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidLocationRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidLocationRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(LocationConflictException.class)
    ProblemDetail handleConflict(LocationConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setType(URI.create(
            exception.getReason() == LocationConflictException.Reason.ACTIVE_ORDERS
                ? "urn:kairos:problem:location-active-orders"
                : "urn:kairos:problem:location-conflict"
        ));
        return problem;
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
