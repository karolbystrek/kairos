package pl.karolbystrek.kairos.api.authentication.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Staff credentials and email verification codes; joins the caller's transaction. */
@Component
@RequiredArgsConstructor
public class StaffIdentityStore {
    public record PendingUser(String id, String code) {
        @Override public String toString() { return "PendingUser[redacted]"; }
    }
    public record UserEmail(String id, String email, boolean verified) {}

    static final Duration CODE_LIFETIME = Duration.ofHours(1);
    // PBKDF2 needs no extra dependency and, unlike BCrypt, accepts the 200-character password limit.
    private static final PasswordEncoder PASSWORDS = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    // Checking unknown emails against a real hash keeps them as slow as wrong passwords.
    private static final String UNKNOWN_IDENTITY_HASH = PASSWORDS.encode("unknown-identity");
    private final JdbcTemplate database;
    private final OneTimeBearerTokenService tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String createUser(String email, String password) {
        var subject = UUID.randomUUID().toString();
        try {
            database.update("INSERT INTO staff_identities(subject,email,password_hash) VALUES (?,?,?)",
                subject, email, PASSWORDS.encode(password));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists");
        }
        return subject;
    }

    public PendingUser createUnverifiedUser(String email, String password) {
        var subject = createUser(email, password);
        return new PendingUser(subject, resendEmailCode(subject));
    }

    public String signIn(String email, String password) {
        var rows = database.queryForList("SELECT subject,password_hash FROM staff_identities WHERE email=?", email);
        var hash = rows.isEmpty() ? UNKNOWN_IDENTITY_HASH : (String) rows.getFirst().get("password_hash");
        if (!PASSWORDS.matches(password, hash) || rows.isEmpty()) throw new InvalidLoginException();
        return (String) rows.getFirst().get("subject");
    }

    public boolean exists(String subject) {
        return Boolean.TRUE.equals(database.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM staff_identities WHERE subject=?)", Boolean.class, subject));
    }

    public Optional<UserEmail> findByEmail(String email) {
        return database.query("SELECT subject,email,email_verified FROM staff_identities WHERE email=?",
            (row, number) -> new UserEmail(row.getString(1), row.getString(2), row.getBoolean(3)), email).stream().findFirst();
    }

    public UserEmail userEmail(String subject) {
        return database.query("SELECT subject,email,email_verified FROM staff_identities WHERE subject=?",
                (row, number) -> new UserEmail(row.getString(1), row.getString(2), row.getBoolean(3)), subject)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));
    }

    /** Replaces any earlier code, so only the latest emailed code verifies. */
    public String resendEmailCode(String subject) {
        var code = "%06d".formatted(random.nextInt(1_000_000));
        if (database.update("UPDATE staff_identities SET verification_code_hash=?,verification_code_expires_at=? WHERE subject=?",
                codeHash(subject, code), Timestamp.from(clock.instant().plus(CODE_LIFETIME)), subject) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found");
        return code;
    }

    public void verifyEmail(String subject, String code) {
        if (database.update("""
                UPDATE staff_identities SET email_verified=true,verification_code_hash=NULL,verification_code_expires_at=NULL
                WHERE subject=? AND verification_code_hash=? AND verification_code_expires_at>?
                """, subject, codeHash(subject, code), Timestamp.from(clock.instant())) == 0)
            throw new RegistrationValidationException(HttpStatus.BAD_REQUEST, "code",
                "The code is incorrect or expired. Check it or request a new code.");
    }

    public void markEmailVerified(String subject, String email) {
        database.update("UPDATE staff_identities SET email_verified=true,verification_code_hash=NULL,verification_code_expires_at=NULL"
            + " WHERE subject=? AND email=?", subject, email);
    }

    public void changePassword(String subject, String current, String replacement) {
        var hash = database.query("SELECT password_hash FROM staff_identities WHERE subject=? FOR UPDATE",
            (row, number) -> row.getString(1), subject).stream().findFirst().orElse(UNKNOWN_IDENTITY_HASH);
        if (!PASSWORDS.matches(current, hash) || hash.equals(UNKNOWN_IDENTITY_HASH))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check the current password and password requirements");
        database.update("UPDATE staff_identities SET password_hash=? WHERE subject=?", PASSWORDS.encode(replacement), subject);
    }

    public void deleteUser(String subject) {
        database.update("DELETE FROM staff_identities WHERE subject=?", subject);
    }

    private String codeHash(String subject, String code) { return tokens.hash(subject + ":" + code); }
}
