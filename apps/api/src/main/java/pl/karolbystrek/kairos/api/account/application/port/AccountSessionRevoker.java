package pl.karolbystrek.kairos.api.account.application.port;

import java.util.UUID;

public interface AccountSessionRevoker {

    void revokeAll(UUID accountId);
}
