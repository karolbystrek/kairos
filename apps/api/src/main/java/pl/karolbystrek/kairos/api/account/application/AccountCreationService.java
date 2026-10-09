package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;

import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class AccountCreationService {
    private final JdbcTemplate database;

    @Transactional(readOnly = true)
    public boolean identityConflict(String email, String subject) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT public.identity_conflict(?, ?)", Boolean.class, email, subject));
    }

    public static RuntimeException bootstrapFailure(DataAccessException failure) {
        if (failure.getMostSpecificCause() instanceof SQLException sql) {
            if ("23505".equals(sql.getSQLState()) || sql.getMessage().contains("IDENTITY_CONFLICT")) {
                return new AccountConflictException("An account with the supplied identity already exists");
            }
            if ("P0001".equals(sql.getSQLState())) {
                if (sql.getMessage().contains("INVITATION_NOT_FOUND")) return new AccountInvitationNotFoundException();
                for (var reason : AccountInvitationUnavailableException.Reason.values()) {
                    if (sql.getMessage().contains("INVITATION_" + reason.name())) {
                        return new AccountInvitationUnavailableException(reason);
                    }
                }
            }
        }
        return failure;
    }
}
