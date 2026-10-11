package pl.karolbystrek.kairos.api.integration.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.integration.infrastructure.config.ExternalIntegrationProperties;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyAuthenticationRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApiKeyAuthenticationService {

    private static final String INVALID_CREDENTIAL_MESSAGE = "Invalid API Key";

    private final ApiKeyAuthenticationRepository credentialsRepository;
    private final DatabaseAccessContext databaseAccess;
    private final ApiKeyCredentialService credentialService;
    private final ExternalIntegrationProperties properties;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate usageTransaction;

    public ApiKeyAuthenticationService(
            ApiKeyAuthenticationRepository credentialsRepository,
            DatabaseAccessContext databaseAccess,
            ApiKeyCredentialService credentialService,
            ExternalIntegrationProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.credentialsRepository = credentialsRepository;
        this.databaseAccess = databaseAccess;
        this.credentialService = credentialService;
        this.properties = properties;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.usageTransaction = new TransactionTemplate(transactionManager);
        this.usageTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ApiKeyPrincipal authenticate(String credential) {
        var apiKeyId = parseApiKeyId(credential);
        var now = clock.instant();
        var usageStaleAt = now.minus(properties.apiKeyUsageRefresh());
        var authenticated = readTransaction.execute(status -> {
            var credentials = credentialsRepository.findCredentials(apiKeyId, now, usageStaleAt)
                .orElseThrow(ApiKeyAuthenticationService::invalidCredential);
            if (!credentialService.matches(credential, credentials.getSecretHash())) throw invalidCredential();
            var scopes = Arrays.stream(credentials.getScopes())
                .map(ApiKeyScope::valueOf)
                .collect(Collectors.toUnmodifiableSet());
            var locations = Arrays.stream(credentials.getLocationIds())
                .collect(Collectors.toUnmodifiableSet());
            var principal = new ApiKeyPrincipal(credentials.getTenantId(), credentials.getIntegrationId(),
                credentials.getApiKeyId(), scopes, locations);
            databaseAccess.integration(principal);
            return new Authenticated(principal, Boolean.TRUE.equals(credentials.getUsageRecordDue()));
        });
        if (authenticated.usageRecordDue()) recordUse(authenticated.principal(), now, usageStaleAt);
        return authenticated.principal();
    }

    private void recordUse(ApiKeyPrincipal principal, Instant now, Instant usageStaleAt) {
        try {
            usageTransaction.executeWithoutResult(status -> {
                databaseAccess.integration(principal);
                credentialsRepository.recordUse(principal.apiKeyId(), now, usageStaleAt);
            });
        } catch (RuntimeException exception) {
            // Last-used time is informational; a failed update must not reject a valid request.
            log.warn("Could not record usage of API Key {}", principal.apiKeyId(), exception);
        }
    }

    private UUID parseApiKeyId(String credential) {
        try {
            return credentialService.parseApiKeyId(credential);
        } catch (IllegalArgumentException exception) {
            throw invalidCredential();
        }
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException(INVALID_CREDENTIAL_MESSAGE);
    }

    private record Authenticated(ApiKeyPrincipal principal, boolean usageRecordDue) {
    }
}
