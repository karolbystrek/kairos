package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationRequest;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationResponse;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationService;

@RestController
@RequiredArgsConstructor
class TenantRegistrationController {

    private final TenantRegistrationService tenantRegistrationService;

    @PostMapping("/tenant-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    TenantRegistrationResponse register(
        @Valid @RequestBody TenantRegistrationRequest request
    ) {
        var administrator = request.administrator();
        var registration = tenantRegistrationService.register(
            administrator.username(),
            administrator.email(),
            administrator.password()
        );
        return TenantRegistrationResponse.from(registration);
    }
}
