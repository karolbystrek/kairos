package pl.karolbystrek.kairos.api.email.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.email.domain.EmailDeliveryState;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EmailDeliveryIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired private EmailDeliveryService deliveries;
    @Autowired private EmailDeliveryWorker worker;
    @Autowired private DatabaseAccessContext access;
    @Autowired private PlatformTransactionManager transactions;
    private final JdbcTemplate owner = PostgresTestDatabase.ownerDatabase();

    @Test
    void acceptedMailIsEncryptedDurablyAndTerminalProcessingRemovesItsSecrets() {
        var subject = UUID.randomUUID().toString();
        var id = new TransactionTemplate(transactions).execute(status -> {
            access.verification(subject);
            return deliveries.enqueue("owner@example.com", "Verify your email", "Your code is 123456");
        });
        var ciphertext = owner.queryForObject("SELECT encrypted_payload FROM email_deliveries WHERE id=?", byte[].class, id);
        assertThat(new String(ciphertext)).doesNotContain("123456", "owner@example.com");
        assertThat(owner.queryForObject("SELECT state FROM email_deliveries WHERE id=?", String.class, id)).isEqualTo("QUEUED");
        worker.deliverAvailable();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.verification(subject);
            assertThat(deliveries.state(id)).isEqualTo(EmailDeliveryState.SENT);
        });
        assertThat(owner.queryForObject("SELECT encrypted_payload FROM email_deliveries WHERE id=?", byte[].class, id)).isNull();
        worker.deliverAvailable();
        assertThat(owner.queryForObject("SELECT attempt_count FROM email_deliveries WHERE id=?", Integer.class, id)).isEqualTo(1);
    }
    @Test
    void anotherVerificationIdentityCannotReadOrCancelMailAndCancellationRemovesSecrets() {
        var subject = UUID.randomUUID().toString();
        var id = new TransactionTemplate(transactions).execute(status -> {
            access.verification(subject);
            return deliveries.enqueue("owner@example.com", "Verify", "123456");
        });
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.verification("different-" + subject);
            deliveries.cancel(id);
            assertThatThrownBy(() -> deliveries.state(id)).isInstanceOf(EmptyResultDataAccessException.class);
            status.setRollbackOnly();
        });
        assertThat(owner.queryForObject("SELECT state FROM email_deliveries WHERE id=?", String.class, id)).isEqualTo("QUEUED");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.verification(subject);
            deliveries.cancel(id);
            assertThat(deliveries.state(id)).isEqualTo(EmailDeliveryState.FAILED);
        });
        assertThat(owner.queryForObject("SELECT encrypted_payload FROM email_deliveries WHERE id=?", byte[].class, id)).isNull();
    }

    @Test
    void expiredIdempotencyWindowFailsWithoutAnotherSendAndOldClaimCannotComplete() {
        var subject = UUID.randomUUID().toString();
        var id = new TransactionTemplate(transactions).execute(status -> {
            access.verification(subject);
            return deliveries.enqueue("owner@example.com", "Verify", "123456");
        });
        owner.update("UPDATE email_deliveries SET first_attempt_at=current_timestamp-interval '24 hours' WHERE id=?", id);
        worker.deliverAvailable();
        assertThat(owner.queryForObject("SELECT state FROM email_deliveries WHERE id=?", String.class, id)).isEqualTo("FAILED");
        assertThat(owner.queryForObject("SELECT attempt_count FROM email_deliveries WHERE id=?", Integer.class, id)).isZero();
        assertThat(owner.queryForObject("SELECT encrypted_payload FROM email_deliveries WHERE id=?", byte[].class, id)).isNull();
        assertThat(owner.queryForObject("SELECT public.complete_email_delivery(?,?,?,current_timestamp,NULL)", Boolean.class,
            id, UUID.randomUUID(), "SENT")).isFalse();
    }

    @Test
    void leaseRecoveryFencesThePreviousWorkerCompletion() {
        var subject = UUID.randomUUID().toString();
        var id = new TransactionTemplate(transactions).execute(status -> {
            access.verification(subject);
            return deliveries.enqueue("owner@example.com", "Verify", "123456");
        });
        owner.update("UPDATE email_deliveries SET created_at='2000-01-01',next_attempt_at='2000-01-01' WHERE id=?", id);
        var previous = UUID.randomUUID();
        assertThat(owner.queryForObject("SELECT id FROM public.claim_email_delivery(current_timestamp,?)", UUID.class, previous)).isEqualTo(id);
        owner.update("UPDATE email_deliveries SET claim_until=current_timestamp-interval '1 second' WHERE id=?", id);
        var recovered = UUID.randomUUID();
        assertThat(owner.queryForObject("SELECT id FROM public.claim_email_delivery(current_timestamp,?)", UUID.class, recovered)).isEqualTo(id);
        assertThat(owner.queryForObject("SELECT public.complete_email_delivery(?,?,?,current_timestamp,NULL)", Boolean.class,
            id, previous, "SENT")).isFalse();
        assertThat(owner.queryForObject("SELECT public.complete_email_delivery(?,?,?,current_timestamp,NULL)", Boolean.class,
            id, recovered, "SENT")).isTrue();
    }

}
