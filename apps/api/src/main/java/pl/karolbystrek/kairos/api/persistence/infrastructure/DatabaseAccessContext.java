package pl.karolbystrek.kairos.api.persistence.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DatabaseAccessContext {
    private final JdbcTemplate database;
    private final java.time.Clock clock;

    public void staff(StaffPrincipal principal) {
        requireTransaction();
        if (principal == null || !Boolean.TRUE.equals(database.queryForObject(
                "SELECT public.valid_staff(?, ?, ?)", Boolean.class,
                principal.accountId(), principal.tenantId(), principal.tenantRole().name()))) {
            throw new StaffAccessDeniedException("The staff account is not eligible");
        }
        bind("staff", principal.accountId().toString(), "", "");
    }

    public void lockStaffLocation() {
        requireTransaction();
        if (!Boolean.TRUE.equals(database.queryForObject("SELECT public.lock_staff_location()", Boolean.class))) {
            throw new StaffAccessDeniedException("The staff account is not eligible");
        }
    }

    public boolean lockAccountLocation(UUID accountId) {
        requireTransaction();
        return Boolean.TRUE.equals(database.queryForObject("SELECT public.lock_account_location(?)", Boolean.class, accountId));
    }

    public void integration(ApiKeyPrincipal principal) {
        integration(principal, "integration");
    }

    public void integrationCommand(ApiKeyPrincipal principal) {
        integration(principal, "integration-command");
    }

    private void integration(ApiKeyPrincipal principal, String scope) {
        requireTransaction();
        if (principal == null || !Boolean.TRUE.equals(database.queryForObject(
                "SELECT public.valid_integration(?, ?, ?, ?, ?::text[], ?::uuid[], ?)", Boolean.class,
                principal.apiKeyVersionId(), principal.apiKeyId(), principal.integrationId(), principal.tenantId(),
                principal.scopes().stream().map(Enum::name).sorted().collect(Collectors.joining(",", "{", "}")),
                principal.locationIds().stream().map(UUID::toString).sorted().collect(Collectors.joining(",", "{", "}")), java.sql.Timestamp.from(clock.instant())))) {
            throw new IntegrationAccessDeniedException("The API Key is not eligible");
        }
        bind(scope, principal.apiKeyVersionId().toString(), "", "");
    }

    public void registration(UUID tenantId, UUID accountId) {
        bind("registration", tenantId.toString(), accountId.toString(), "");
    }

    public void invitation(String tokenHash, UUID accountId) {
        bind("invitation", tokenHash, accountId == null ? "" : accountId.toString(), "");
    }

    public void trackedOrders(Collection<UUID> references) {
        bind("tracking", "", identifiers(references), "");
    }

    public void pushCapability(Collection<UUID> subscriptions, Collection<UUID> references) {
        bind("push", "", identifiers(references), identifiers(subscriptions));
    }

    public boolean worker(WorkerOperation operation, UUID resourceId, UUID claimToken) {
        requireTransaction();
        if (!Boolean.TRUE.equals(database.queryForObject("SELECT public.valid_worker(?, ?, ?)",
                Boolean.class, operation.name(), resourceId, claimToken))) {
            return false;
        }
        bind(operation.name(), resourceId.toString(), "", "");
        return true;
    }

    private void bind(String scope, String identity, String references, String subscriptions) {
        requireTransaction();
        var previous = database.queryForMap("""
            SELECT COALESCE(current_setting('kairos.scope', true),'') AS scope,
                   COALESCE(current_setting('kairos.identity', true),'') AS identity,
                   COALESCE(current_setting('kairos.references', true),'') AS references,
                   COALESCE(current_setting('kairos.subscriptions', true),'') AS subscriptions
            """);
        if (!previous.get("scope").equals("") && (!previous.get("scope").equals(scope)
                || !previous.get("identity").equals(identity) || !previous.get("references").equals(references)
                || !previous.get("subscriptions").equals(subscriptions))) {
            throw new IllegalStateException("Cannot change database authority in a joined transaction");
        }
        database.queryForMap("""
            SELECT set_config('kairos.at_time', ?, true), set_config('kairos.scope', ?, true), set_config('kairos.identity', ?, true),
                   set_config('kairos.references', ?, true), set_config('kairos.subscriptions', ?, true)
            """, clock.instant().toString(), scope, identity, references, subscriptions);
    }

    private static String identifiers(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).distinct().sorted().collect(Collectors.joining(","));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Database authority requires a transaction");
        }
    }
}
