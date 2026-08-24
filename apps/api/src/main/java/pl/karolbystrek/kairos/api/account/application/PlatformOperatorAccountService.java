package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.FinalPlatformOperatorConfirmationRequiredException;
import pl.karolbystrek.kairos.api.account.application.exception.PlatformOperatorAccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.application.port.TenantRegistrationInvitationRevoker;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.LocalAuthenticationService;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformOperatorAccountService {

    private final AccountRepository accountRepository;
    private final AccountCreationService accountCreationService;
    private final AccountSessionRevoker sessionRevoker;
    private final TenantRegistrationInvitationRevoker invitationRevoker;
    private final Clock clock;

    @Transactional
    public Account provision(String username, String email, String password) {
        var account = accountCreationService.createPlatformOperator(username, email, password);
        log.info("Provisioned Platform Operator account {}", account.getId());
        return account;
    }

    @Transactional
    public void disable(String username, boolean finalOperatorConfirmed) {
        var normalizedUsername = LocalAuthenticationService.normalizeUsername(username);
        var operators = accountRepository.findAllForUpdateByKindOrderById(
            AccountKind.PLATFORM_OPERATOR
        );
        var account = operators.stream()
            .filter(candidate -> candidate.getUsername().equals(normalizedUsername))
            .findFirst()
            .orElseThrow(PlatformOperatorAccountNotFoundException::new);
        if (account.isDisabled()) {
            return;
        }
        var enabledOperatorCount = operators.stream()
            .filter(candidate -> candidate.getStatus() == AccountStatus.ENABLED)
            .count();
        if (enabledOperatorCount == 1 && !finalOperatorConfirmed) {
            throw new FinalPlatformOperatorConfirmationRequiredException();
        }

        account.disable(clock.instant());
        sessionRevoker.revokeAll(account.getId());
        invitationRevoker.revokePendingByIssuer(account.getId());
        log.info("Disabled Platform Operator account {}", account.getId());
    }

    @Transactional
    public void enable(String username) {
        var account = findOperatorForUpdate(username);
        account.enable(clock.instant());
        log.info("Enabled Platform Operator account {}", account.getId());
    }

    private Account findOperatorForUpdate(String username) {
        var normalizedUsername = LocalAuthenticationService.normalizeUsername(username);
        return accountRepository.findForUpdateByUsername(normalizedUsername)
            .filter(account -> account.getKind() == AccountKind.PLATFORM_OPERATOR)
            .orElseThrow(PlatformOperatorAccountNotFoundException::new);
    }
}
