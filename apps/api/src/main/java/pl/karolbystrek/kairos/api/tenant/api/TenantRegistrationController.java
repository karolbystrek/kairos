package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationRateLimiter;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationRequest;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationResponse;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationService;

@RestController
@RequiredArgsConstructor
class TenantRegistrationController {

    private final TenantRegistrationService tenantRegistrationService;
    private final AuthenticationRateLimiter rateLimiter;

    @PostMapping("/tenant-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    TenantRegistrationResponse register(
        @Valid @RequestBody TenantRegistrationRequest request,
        HttpServletRequest servletRequest
    ) {
        rateLimiter.checkTenantRegistration(clientAddress(servletRequest));
        var administrator = request.administrator();
        var registration = tenantRegistrationService.register(
            request.tenantName(),
            request.locationName(),
            administrator.username(),
            administrator.email(),
            administrator.password(),
            administrator.displayName()
        );
        return TenantRegistrationResponse.from(registration);
    }

    private static String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
