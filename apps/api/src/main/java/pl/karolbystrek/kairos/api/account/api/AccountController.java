package pl.karolbystrek.kairos.api.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.ManagedAccountResponse;
import pl.karolbystrek.kairos.api.account.api.model.ProvisionAccountRequest;
import pl.karolbystrek.kairos.api.account.api.model.UpdateAccountStatusRequest;
import pl.karolbystrek.kairos.api.account.application.AccountProvisioningService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/accounts/v1")
@RequiredArgsConstructor
class AccountController {

    private final AccountProvisioningService accountProvisioningService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ManagedAccountResponse provisionAccount(
        @AuthenticationPrincipal StaffPrincipal principal,
        @Valid @RequestBody ProvisionAccountRequest request
    ) {
        var account = accountProvisioningService.provision(
            principal,
            request.locationId(),
            request.username(),
            request.email(),
            request.password(),
            request.role()
        );
        return ManagedAccountResponse.from(account);
    }

    @PatchMapping("/{accountId}/status")
    ManagedAccountResponse updateAccountStatus(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID accountId,
        @Valid @RequestBody UpdateAccountStatusRequest request
    ) {
        var account = accountProvisioningService.changeStatus(principal, accountId, request.status());
        return ManagedAccountResponse.from(account);
    }
}
