package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class SecurityProblemDetailsHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final URI UNAUTHORIZED_TYPE = URI.create("urn:kairos:problem:unauthorized");
    private static final URI FORBIDDEN_TYPE = URI.create("urn:kairos:problem:forbidden");
    private static final URI MISSING_CSRF_TYPE = URI.create("urn:kairos:problem:csrf-token-missing");
    private static final URI INVALID_CSRF_TYPE = URI.create("urn:kairos:problem:csrf-token-invalid");

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(
                response,
                HttpStatus.UNAUTHORIZED,
                UNAUTHORIZED_TYPE,
                "Authentication required",
                "A valid staff access credential is required"
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        if (exception instanceof MissingCsrfTokenException) {
            write(
                    response,
                    HttpStatus.FORBIDDEN,
                    MISSING_CSRF_TYPE,
                    "CSRF token required",
                    "A CSRF token is required for this request"
            );
            return;
        }
        if (exception instanceof InvalidCsrfTokenException) {
            write(
                    response,
                    HttpStatus.FORBIDDEN,
                    INVALID_CSRF_TYPE,
                    "Invalid CSRF token",
                    "The supplied CSRF token is invalid or no longer current"
            );
            return;
        }

        write(
                response,
                HttpStatus.FORBIDDEN,
                FORBIDDEN_TYPE,
                "Access denied",
                "The authenticated account is not permitted to perform this operation"
        );
    }

    private void write(
            HttpServletResponse response,
            HttpStatus status,
            URI type,
            String title,
            String detail
    ) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
