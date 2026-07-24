package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RateLimitExceededException;

@RestControllerAdvice(basePackageClasses = TenantRegistrationController.class)
class TenantRegistrationExceptionHandler {

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail handleConflict(AccountConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail handleRateLimit(
        RateLimitExceededException exception,
        HttpServletResponse response
    ) {
        var retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            exception.getMessage()
        );
    }
}
