package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffAuthenticationRepository;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StaffAuthenticationService {
    private final StaffAuthenticationRepository credentialsRepository;
    private final StaffAccessService access;

    @Transactional(readOnly = true)
    public StaffPrincipal authenticate(ZitadelClient.ProviderSession identity, Instant signedInAt) {
        var credentials = credentialsRepository.findCredentials(identity.userId())
            .orElseThrow(() -> new StaffAccessDeniedException("Account is unavailable"));
        var cutoff = credentials.getAuthenticationCutoff();
        if (signedInAt == null || (cutoff != null && !signedInAt.isAfter(cutoff.toInstant()))) {
            throw new StaffAccessDeniedException("Session was revoked");
        }
        var principal = new StaffPrincipal(credentials.getAccountId(), credentials.getTenantId(),
            TenantRole.valueOf(credentials.getTenantRole()));
        access.resolve(principal);
        return principal;
    }
}
