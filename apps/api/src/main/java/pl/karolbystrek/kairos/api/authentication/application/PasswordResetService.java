package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;
import pl.karolbystrek.kairos.api.email.application.EmailDeliveryService;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Forgotten-password recovery with emailed one-hour codes; answers never reveal whether an account exists. */
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    static final Duration CODE_LIFETIME = Duration.ofHours(1);
    static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    static final int MAXIMUM_ATTEMPTS = 5;
    private final JdbcTemplate database;
    private final DatabaseAccessContext access;
    private final StaffIdentityStore identities;
    private final OneTimeBearerTokenService tokens;
    private final EmailDeliveryService mail;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Emails a new code to an account that may sign in, at most once per resend delay. */
    public void request(String email) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var subject = lockedSubject(email);
            if (subject == null) return;
            var now = clock.instant();
            var rows = database.queryForList("SELECT resend_available_at,delivery_id FROM password_resets WHERE subject=? FOR UPDATE", subject);
            if (!rows.isEmpty()) {
                if (((Timestamp) rows.getFirst().get("resend_available_at")).toInstant().isAfter(now)) return;
                mail.cancel((UUID) rows.getFirst().get("delivery_id"));
            }
            var code = "%06d".formatted(random.nextInt(1_000_000));
            var delivery = mail.enqueue(email, "Reset hasła Kairos", "Twój kod do zmiany hasła w Kairos to " + code
                + ".\nWpisz go na ekranie resetu hasła. Kod jest ważny przez godzinę.\n"
                + "Jeśli nie prosisz o zmianę hasła, zignoruj tę wiadomość. Twoje hasło się nie zmieni.");
            database.update("""
                INSERT INTO password_resets(subject,code_hash,expires_at,attempts,resend_available_at,delivery_id)
                VALUES (?,?,?,0,?,?)
                ON CONFLICT (subject) DO UPDATE SET code_hash=EXCLUDED.code_hash,expires_at=EXCLUDED.expires_at,attempts=0,
                    resend_available_at=EXCLUDED.resend_available_at,delivery_id=EXCLUDED.delivery_id
                """, subject, hash(subject, code), timestamp(now.plus(CODE_LIFETIME)), timestamp(now.plus(RESEND_DELAY)), delivery);
        });
    }

    /** Sets the password when the latest code is correct, then ends every session of the account. */
    public void reset(String email, String code, String password) {
        var error = new TransactionTemplate(transactions).execute(status -> {
            var subject = lockedSubject(email);
            if (subject == null) return invalidCode();
            var rows = database.queryForList("SELECT code_hash,expires_at,attempts FROM password_resets WHERE subject=? FOR UPDATE", subject);
            if (rows.isEmpty()) return invalidCode();
            var row = rows.getFirst();
            if (((Number) row.get("attempts")).intValue() >= MAXIMUM_ATTEMPTS) {
                return new RegistrationValidationException(HttpStatus.BAD_REQUEST, "code", "Request a new code before trying again.");
            }
            var expected = ((String) row.get("code_hash")).getBytes(StandardCharsets.US_ASCII);
            if (!((Timestamp) row.get("expires_at")).toInstant().isAfter(clock.instant())
                    || !MessageDigest.isEqual(expected, hash(subject, code).getBytes(StandardCharsets.US_ASCII))) {
                // The failed attempt must commit, so the error is thrown only after the transaction.
                database.update("UPDATE password_resets SET attempts=attempts+1 WHERE subject=?", subject);
                return invalidCode();
            }
            identities.resetPassword(subject, email, password);
            if (!Boolean.TRUE.equals(database.queryForObject("SELECT public.revoke_reset_sessions(?)", Boolean.class,
                    timestamp(clock.instant())))) {
                throw new IllegalStateException("Password reset could not revoke existing sessions");
            }
            database.update("DELETE FROM password_resets WHERE subject=?", subject);
            return null;
        });
        if (error != null) throw error;
    }

    // Shares the registration lock, so a reset and a registration for one email never interleave.
    private String lockedSubject(String email) {
        database.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", email);
        var subject = database.queryForObject("SELECT public.password_reset_subject(?)", String.class, email);
        if (subject != null) access.verification(subject);
        return subject;
    }

    private String hash(String subject, String code) { return tokens.hash("password-reset:" + subject + ":" + code); }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private static RegistrationValidationException invalidCode() {
        return new RegistrationValidationException(HttpStatus.BAD_REQUEST, "code",
            "The code is incorrect or expired. Check it or request a new code.");
    }
}
