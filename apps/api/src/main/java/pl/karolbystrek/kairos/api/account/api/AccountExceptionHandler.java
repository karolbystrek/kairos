package pl.karolbystrek.kairos.api.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.SignedInRedemptionException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.authentication.api.AuthenticationController;

import java.net.URI;
import java.util.Locale;

@RestControllerAdvice(basePackageClasses = {AccountController.class, AuthenticationController.class})
class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(AccountNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail handleConflict(AccountConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidAccountRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail handleAccessDenied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(AccountInvitationNotFoundException.class)
    ProblemDetail handleInvalidInvitation(AccountInvitationNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("urn:kairos:problem:account-invitation-invalid"));
        return problem;
    }

    @ExceptionHandler(AccountInvitationUnavailableException.class)
    ProblemDetail handleUnavailableInvitation(AccountInvitationUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
        problem.setType(URI.create(
            "urn:kairos:problem:account-invitation-"
                + exception.reason().name().toLowerCase(Locale.ROOT)
        ));
        return problem;
    }

    @ExceptionHandler(SignedInRedemptionException.class)
    ProblemDetail handleSignedInRedemption(SignedInRedemptionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
