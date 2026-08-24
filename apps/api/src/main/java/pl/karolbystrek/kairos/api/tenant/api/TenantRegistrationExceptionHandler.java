package pl.karolbystrek.kairos.api.tenant.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.tenant.application.exception.InvalidTenantRegistrationInvitationRequestException;
import pl.karolbystrek.kairos.api.tenant.application.exception.SignedInTenantRegistrationException;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationNotFoundException;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationUnavailableException;

import java.net.URI;
import java.util.Locale;

@RestControllerAdvice(basePackageClasses = TenantRegistrationController.class)
class TenantRegistrationExceptionHandler {

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail handleConflict(AccountConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({
        InvalidTenantRegistrationInvitationRequestException.class
    })
    ProblemDetail handleInvalidRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({StaffAccessDeniedException.class, SignedInTenantRegistrationException.class})
    ProblemDetail handleAccessDenied(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(TenantRegistrationInvitationNotFoundException.class)
    ProblemDetail handleInvalidInvitation(TenantRegistrationInvitationNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("urn:kairos:problem:tenant-registration-invitation-invalid"));
        return problem;
    }

    @ExceptionHandler(TenantRegistrationInvitationUnavailableException.class)
    ProblemDetail handleUnavailableInvitation(TenantRegistrationInvitationUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
        problem.setType(URI.create(
            "urn:kairos:problem:tenant-registration-invitation-"
                + exception.reason().name().toLowerCase(Locale.ROOT)
        ));
        return problem;
    }
}
