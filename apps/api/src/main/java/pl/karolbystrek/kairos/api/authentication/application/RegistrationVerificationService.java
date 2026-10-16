package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;
import pl.karolbystrek.kairos.api.email.application.EmailDeliveryService;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationService;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RegistrationVerificationService {
    public record Pending(String subject, String email) implements Serializable {}
    public record State(boolean verificationRequired, String email, Instant resendAvailableAt) {}
    private record Completion(StaffPrincipal principal, RegistrationValidationException error) {}
    private final StaffIdentityStore identities;
    private final AccountCreationService accounts;
    private final TenantRegistrationService tenants;
    private final StaffAuthenticationService authentication;
    private final EmailDeliveryService mail;
    private final JdbcTemplate database;
    private final DatabaseAccessContext access;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    private Semaphore nestedFlows;
    @Value("${kairos.authentication.email-verification-enabled:true}")
    private boolean emailVerificationEnabled;

    public boolean verificationRequired() { return emailVerificationEnabled; }

    // Nested flows hold one connection while opening a REQUIRES_NEW one; staying below the pool size guarantees progress.
    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    void limitNestedFlows(int poolSize) {
        nestedFlows = new Semaphore(Math.max(1, poolSize / 2));
    }

    /** Runs a flow that may open a nested transaction; call it before any connection is taken. */
    public <T> T nested(Supplier<T> flow) {
        try {
            if (!nestedFlows.tryAcquire(10, TimeUnit.SECONDS))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Registration is busy. Try again.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Registration is busy. Try again.");
        }
        try { return flow.get(); }
        finally { nestedFlows.release(); }
    }

    public Pending register(String email, String password) {
        return nested(() -> new TransactionTemplate(transactions).execute(status -> {
            prepareIdentity(email);
            StaffIdentityStore.PendingUser user;
            try { user = identities.createUnverifiedUser(email, password); }
            catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == 409) throw conflict();
                throw exception;
            }
            if (!emailVerificationEnabled) return new Pending(user.id(), email);
            access.verification(user.id());
            var delivery = send(email, user.code());
            database.update("INSERT INTO pending_registrations(subject,email,attempts,resend_available_at,delivery_id) VALUES (?,?,0,?,?)",
                user.id(), email, Timestamp.from(clock.instant().plusSeconds(60)), delivery);
            return new Pending(user.id(), email);
        }));
    }

    public Pending resume(String subject, String email) {
        return new TransactionTemplate(transactions).execute(status -> {
            lockEmail(email);
            requireNoAccount(email, subject);
            var user = identities.userEmail(subject);
            if (!email.equalsIgnoreCase(user.email())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            access.verification(subject);
            if (database.queryForObject("SELECT COUNT(*) FROM pending_registrations WHERE subject = ?", Integer.class, subject) == 0) {
                if (user.verified()) throw conflict();
                if (!emailVerificationEnabled) return new Pending(subject, email);
                var delivery = send(email, identities.resendEmailCode(subject));
                database.update("INSERT INTO pending_registrations(subject,email,attempts,resend_available_at,delivery_id) VALUES (?,?,0,?,?)",
                    subject, email, Timestamp.from(clock.instant().plusSeconds(60)), delivery);
            }
            return new Pending(subject, email);
        });
    }

    public State state(Pending pending) {
        return new TransactionTemplate(transactions).execute(status -> {
            access.verification(pending.subject());
            var rows = database.queryForList("SELECT resend_available_at FROM pending_registrations WHERE subject = ?", pending.subject());
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registration is unavailable. Sign in again.");
            return new State(true, pending.email(), ((Timestamp) rows.getFirst().get("resend_available_at")).toInstant());
        });
    }

    public State resend(Pending pending) {
        return new TransactionTemplate(transactions).execute(status -> {
            lockEmail(pending.email());
            validate(pending);
            access.verification(pending.subject());
            var row = pendingRow(pending);
            if (((Timestamp) row.get("resend_available_at")).toInstant().isAfter(clock.instant()))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 60 seconds before requesting a new code.");
            var code = identities.resendEmailCode(pending.subject());
            mail.cancel((UUID) row.get("delivery_id"));
            var delivery = send(pending.email(), code);
            var next = clock.instant().plusSeconds(60);
            database.update("UPDATE pending_registrations SET attempts=0,resend_available_at=?,delivery_id=? WHERE subject=?",
                Timestamp.from(next), delivery, pending.subject());
            return new State(true, pending.email(), next);
        });
    }

    public StaffPrincipal verify(Pending pending, String code) {
        var result = nested(() -> new TransactionTemplate(transactions).execute(status -> {
            lockEmail(pending.email());
            if (!identities.exists(pending.subject())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            // An earlier successful commit may have lost its HTTP response.
            if (accounts.identityConflict(pending.email(), pending.subject()))
                return new Completion(authentication.authenticate(pending.subject(), clock.instant()), null);
            validate(pending);
            access.verification(pending.subject());
            if (!emailVerificationEnabled) {
                identities.markEmailVerified(pending.subject(), pending.email());
                var principal = tenants.register(pending.email(), pending.subject());
                for (var row : database.queryForList("SELECT delivery_id FROM pending_registrations WHERE subject=?", pending.subject()))
                    mail.cancel((UUID) row.get("delivery_id"));
                database.update("DELETE FROM pending_registrations WHERE subject=?", pending.subject());
                return new Completion(principal, null);
            }
            var row = pendingRow(pending);
            if (((Number) row.get("attempts")).intValue() >= 5)
                return new Completion(null, new RegistrationValidationException(HttpStatus.BAD_REQUEST, "code", "Request a new code before trying again."));
            var user = identities.userEmail(pending.subject());
            if (!user.verified()) {
                try { identities.verifyEmail(pending.subject(), code); }
                catch (RegistrationValidationException exception) {
                    database.update("UPDATE pending_registrations SET attempts=attempts+1 WHERE subject=?", pending.subject());
                    return new Completion(null, exception);
                }
            }
            var principal = tenants.register(pending.email(), pending.subject());
            mail.cancel((UUID) row.get("delivery_id"));
            database.update("DELETE FROM pending_registrations WHERE subject=?", pending.subject());
            return new Completion(principal, null);
        }));
        if (result.error() != null) throw result.error();
        return result.principal();
    }

    void prepareIdentity(String email) {
        lockEmail(email);
        requireNoAccount(email, null);
        var existing = identities.findByEmail(email);
        if (existing.isPresent()) {
            var identity = existing.get();
            requireNoAccount(email, identity.id());
            if (identity.verified()) throw conflict();
            replaceUnverified(identity);
        }
    }

    private void replaceUnverified(StaffIdentityStore.UserEmail identity) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            access.verification(identity.id());
            var rows = database.queryForList("SELECT resend_available_at,delivery_id FROM pending_registrations WHERE subject=? FOR UPDATE", identity.id());
            if (!rows.isEmpty() && ((Timestamp) rows.getFirst().get("resend_available_at")).toInstant().isAfter(clock.instant()))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 60 seconds before registering again.");
            identities.deleteUser(identity.id());
            if (!rows.isEmpty()) mail.cancel((UUID) rows.getFirst().get("delivery_id"));
            database.update("DELETE FROM pending_registrations WHERE subject=?", identity.id());
        });
    }

    private Map<String, Object> pendingRow(Pending pending) {
        var rows = database.queryForList("SELECT attempts,resend_available_at,delivery_id FROM pending_registrations WHERE subject=? FOR UPDATE", pending.subject());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registration is unavailable. Sign in again.");
        return rows.getFirst();
    }
    private void validate(Pending pending) {
        if (!identities.exists(pending.subject())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var user = identities.userEmail(pending.subject());
        if (!pending.email().equalsIgnoreCase(user.email())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        requireNoAccount(pending.email(), pending.subject());
    }
    private UUID send(String email, String code) {
        if (!code.matches("[0-9]{6}")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email verification is unavailable");
        return mail.enqueue(email, "Kod weryfikacyjny Kairos", "Twój kod weryfikacyjny Kairos to " + code
            + ".\nWpisz go na ekranie rejestracji. Jeśli kod wygaśnie, wyślij nowy.\nJeśli nie zakładasz konta w Kairos, zignoruj tę wiadomość.");
    }
    private void requireNoAccount(String email, String subject) {
        if (accounts.identityConflict(email, subject)) throw conflict();
    }
    private static RegistrationValidationException conflict() {
        return new RegistrationValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists. Sign in instead.");
    }
    private void lockEmail(String email) {
        database.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", email);
    }
}
