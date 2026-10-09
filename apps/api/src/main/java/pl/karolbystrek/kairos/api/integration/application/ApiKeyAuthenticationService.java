package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private static final String INVALID_CREDENTIAL_MESSAGE = "Invalid API Key";

    private final JdbcTemplate database;
    private final DatabaseAccessContext databaseAccess;
    private final ApiKeyCredentialService credentialService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ApiKeyPrincipal authenticate(String credential) {
        var versionId = parseVersionId(credential);
        var principals = database.query("SELECT * FROM public.api_key_authentication(?, ?)", (row, index) -> {
            if (!credentialService.matches(credential, row.getString("secret_hash"))) throw invalidCredential();
            var scopes = Arrays.stream((String[]) row.getArray("scopes").getArray())
                .map(ApiKeyScope::valueOf)
                .collect(Collectors.toUnmodifiableSet());
            var locations = Arrays.stream((Object[]) row.getArray("location_ids").getArray())
                .map(value -> UUID.fromString(value.toString()))
                .collect(Collectors.toUnmodifiableSet());
            return new ApiKeyPrincipal(row.getObject("tenant_id", UUID.class),
                row.getObject("integration_id", UUID.class), row.getObject("api_key_id", UUID.class),
                row.getObject("api_key_version_id", UUID.class), scopes, locations);
        }, versionId, Timestamp.from(clock.instant()));
        if (principals.isEmpty()) throw invalidCredential();
        var principal = principals.getFirst();
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
