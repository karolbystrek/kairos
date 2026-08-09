package pl.karolbystrek.kairos.api.account.application.port;

import java.util.UUID;
import java.util.Collection;

public interface AccountSessionRevoker {

    void revokeAll(UUID accountId);

    void revokeAll(Collection<UUID> accountIds);
}
