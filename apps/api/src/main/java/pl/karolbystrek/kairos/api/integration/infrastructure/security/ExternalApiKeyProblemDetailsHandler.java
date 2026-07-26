package pl.karolbystrek.kairos.api.integration.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class ExternalApiKeyProblemDetailsHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final URI UNAUTHORIZED_TYPE = URI.create(
            "urn:kairos:problem:external-api-key-unauthorized"
    );
    private static final URI FORBIDDEN_TYPE = URI.create(
            "urn:kairos:problem:external-api-key-forbidden"
    );

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(
                response,
                HttpStatus.UNAUTHORIZED,
                UNAUTHORIZED_TYPE,
                "API Key authentication required",
                "A valid External Integration API Key is required"
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        write(
                response,
                HttpStatus.FORBIDDEN,
                FORBIDDEN_TYPE,
                "Access denied",
                "The API Key is not permitted to perform this operation"
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
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
