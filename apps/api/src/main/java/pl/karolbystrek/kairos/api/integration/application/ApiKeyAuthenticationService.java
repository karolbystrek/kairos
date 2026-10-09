package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyAuthenticationRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private static final String INVALID_CREDENTIAL_MESSAGE = "Invalid API Key";

    private final ApiKeyAuthenticationRepository credentialsRepository;
    private final DatabaseAccessContext databaseAccess;
    private final ApiKeyCredentialService credentialService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ApiKeyPrincipal authenticate(String credential) {
        var versionId = parseVersionId(credential);
        var credentials = credentialsRepository.findCredentials(versionId, clock.instant())
            .orElseThrow(ApiKeyAuthenticationService::invalidCredential);
        if (!credentialService.matches(credential, credentials.getSecretHash())) throw invalidCredential();
        var scopes = Arrays.stream(credentials.getScopes())
            .map(ApiKeyScope::valueOf)
            .collect(Collectors.toUnmodifiableSet());
        var locations = Arrays.stream(credentials.getLocationIds())
            .collect(Collectors.toUnmodifiableSet());
        var principal = new ApiKeyPrincipal(credentials.getTenantId(), credentials.getIntegrationId(),
            credentials.getApiKeyId(), credentials.getApiKeyVersionId(), scopes, locations);
        databaseAccess.integration(principal);
        return principal;
    }

    private UUID parseVersionId(String credential) {
        try {
            return credentialService.parseVersionId(credential);
        } catch (IllegalArgumentException exception) {
            throw invalidCredential();
        }
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException(INVALID_CREDENTIAL_MESSAGE);
    }
}
