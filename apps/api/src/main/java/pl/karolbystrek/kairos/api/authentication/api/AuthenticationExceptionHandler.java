package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidRefreshCredentialException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RateLimitExceededException;

@RestControllerAdvice(basePackageClasses = AuthenticationController.class)
class AuthenticationExceptionHandler {

    @ExceptionHandler({InvalidLoginException.class, InvalidRefreshCredentialException.class})
    ProblemDetail handleInvalidAuthentication(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail handleRateLimit(
        RateLimitExceededException exception,
        HttpServletResponse response
    ) {
        var retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }
}
