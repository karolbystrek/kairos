package pl.karolbystrek.kairos.api.email.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.email.infrastructure.http.ResendEmailSender;
import pl.karolbystrek.kairos.api.email.infrastructure.security.EmailPayloadCipher;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryWorker {
    private record Claimed(UUID id, byte[] encrypted, byte[] nonce, int attempts, Instant firstAttempt) {
        @Override public String toString() { return "Claimed[redacted]"; }
    }
    private final JdbcTemplate database;
    private final PlatformTransactionManager transactions;
    private final EmailPayloadCipher cipher;
    private final ResendEmailSender sender;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${kairos.email.poll-delay:1000}", initialDelayString = "${kairos.email.initial-delay:1000}")
    public void deliverAvailable() {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (var index = 0; index < 20; index++) {
            var token = UUID.randomUUID();
            var claimed = transaction.execute(status -> database.query("SELECT * FROM public.claim_email_delivery(?,?)",
                (row, number) -> new Claimed(row.getObject("id", UUID.class), row.getBytes("encrypted_payload"),
                    row.getBytes("payload_nonce"), row.getInt("attempt_count"), row.getTimestamp("first_attempt_at").toInstant()),
                Timestamp.from(clock.instant()), token).stream().findFirst().orElse(null));
            if (claimed == null) return;
            ResendEmailSender.Result result;
            try {
                result = !clock.instant().isBefore(claimed.firstAttempt().plus(Duration.ofHours(23)))
                    ? ResendEmailSender.Result.FAILED
                    : sender.send(claimed.id(), cipher.decrypt(claimed.id(), claimed.encrypted(), claimed.nonce()));
            } catch (RuntimeException exception) {
                log.error("Could not prepare encrypted email delivery {}", claimed.id());
                result = ResendEmailSender.Result.FAILED;
            }
            var now = clock.instant();
            var retry = now.plusSeconds(Math.min(3600, 5L << Math.min(claimed.attempts() - 1, 10)));
            var state = result == ResendEmailSender.Result.SENT ? "SENT"
                : result == ResendEmailSender.Result.FAILED || !retry.isBefore(claimed.firstAttempt().plus(Duration.ofHours(23)))
                    ? "FAILED" : "QUEUED";
            transaction.executeWithoutResult(status -> database.queryForObject(
                "SELECT public.complete_email_delivery(?,?,?,?,?)", Boolean.class,
                claimed.id(), token, state, Timestamp.from(now), Timestamp.from(retry)));
        }
    }
}
