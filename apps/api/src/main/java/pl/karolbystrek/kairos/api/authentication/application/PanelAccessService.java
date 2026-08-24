package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;

@Service
@RequiredArgsConstructor
public class PanelAccessService {

    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final StaffAccessService staffAccessService;

    public PanelPrincipal principalFor(Account account) {
        return switch (account.getKind()) {
            case TENANT_ACCOUNT -> {
                if (account.getTenantId() == null || account.getTenantRole() == null) {
                    throw ineligible();
                }
                yield new StaffPrincipal(
                    account.getId(),
                    account.getTenantId(),
                    account.getTenantRole()
                );
            }
            case PLATFORM_OPERATOR -> {
                if (account.getTenantId() != null || account.getTenantRole() != null) {
                    throw ineligible();
                }
                yield new PlatformOperatorPrincipal(account.getId());
            }
        };
    }

    @Transactional(readOnly = true)
    public void requireEligible(PanelPrincipal principal) {
        requireEligible(principal, false);
    }

    @Transactional
    public void requireEligibleForUpdate(PanelPrincipal principal) {
        requireEligible(principal, true);
    }

    private void requireEligible(PanelPrincipal principal, boolean lockForUpdate) {
        if (principal instanceof StaffPrincipal staffPrincipal) {
            if (lockForUpdate) {
                staffAccessService.resolveForUpdate(staffPrincipal);
            }
            else {
                staffAccessService.resolve(staffPrincipal);
            }
            return;
        }
        if (!(principal instanceof PlatformOperatorPrincipal operatorPrincipal)) {
            throw ineligible();
        }

        var account = (lockForUpdate
            ? accountRepository.findForUpdateById(operatorPrincipal.accountId())
            : accountRepository.findById(operatorPrincipal.accountId()))
            .orElseThrow(PanelAccessService::ineligible);
        if (account.getKind() != AccountKind.PLATFORM_OPERATOR
            || !account.isEnabled()
            || account.getTenantId() != null
            || account.getTenantRole() != null
            || assignmentRepository.findByIdAccountId(account.getId()).isPresent()) {
            throw ineligible();
        }
    }

    private static StaffAccessDeniedException ineligible() {
        return new StaffAccessDeniedException("The panel account is not eligible");
    }
}
