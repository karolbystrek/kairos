package pl.karolbystrek.kairos.api.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.ManagedAccountResponse;
import pl.karolbystrek.kairos.api.account.api.model.UpdateAccountStatusRequest;
import pl.karolbystrek.kairos.api.account.application.AccountProvisioningService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts/v1")
@RequiredArgsConstructor
class AccountController {

    private final AccountProvisioningService accountProvisioningService;

    @GetMapping
    List<ManagedAccountResponse> listAccounts(
        @AuthenticationPrincipal StaffPrincipal principal
    ) {
        return accountProvisioningService.listManageable(principal).stream()
            .map(ManagedAccountResponse::from)
            .toList();
    }

    @PutMapping("/{accountId}/status")
    ManagedAccountResponse updateAccountStatus(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID accountId,
        @Valid @RequestBody UpdateAccountStatusRequest request
    ) {
        var account = accountProvisioningService.updateStatus(principal, accountId, request.status());
        return ManagedAccountResponse.from(account);
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID accountId
    ) {
        accountProvisioningService.delete(principal, accountId);
    }
}
