package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyVersionRepository;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private static final String INVALID_CREDENTIAL_MESSAGE = "Invalid API Key";

    private final ApiKeyVersionRepository versionRepository;
    private final ApiKeyCredentialService credentialService;
    private final LocationRepository locationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ApiKeyPrincipal authenticate(String credential) {
        var versionId = parseVersionId(credential);
        var version = versionRepository.findById(versionId)
                .orElseThrow(ApiKeyAuthenticationService::invalidCredential);
        var now = clock.instant();
        if (!credentialService.matches(credential, version.getSecretHash())
                || !version.isValidAt(now)
                || !version.getApiKey().canAuthenticateAt(now)) {
            throw invalidCredential();
        }

        var apiKey = version.getApiKey();
        var enabledLocationIds = locationRepository.findAllByIdInAndStatus(
                apiKey.getLocationIds(),
                LocationStatus.ENABLED
            ).stream()
            .filter(location -> location.getTenantId().equals(apiKey.getTenantId()))
            .map(location -> location.getId())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ApiKeyPrincipal(
                apiKey.getTenantId(),
                apiKey.getIntegrationId(),
                apiKey.getId(),
                version.getId(),
                apiKey.getScopes(),
                enabledLocationIds
        );
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
