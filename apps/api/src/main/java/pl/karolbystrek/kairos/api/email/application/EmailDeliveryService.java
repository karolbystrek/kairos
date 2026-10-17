package pl.karolbystrek.kairos.api.email.application;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.email.domain.EmailDeliveryState;
import pl.karolbystrek.kairos.api.email.infrastructure.config.EmailProperties;
import pl.karolbystrek.kairos.api.email.infrastructure.security.EmailPayloadCipher;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {
    private final JdbcTemplate database;
    private final EmailPayloadCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;
    private final EmailProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String to, String subject, String text) {
        return enqueue(to, subject, text, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(@NonNull String to, @NonNull String subject, @NonNull String text, UUID locationId) {
        var id = UUID.randomUUID();
        var payload = cipher.encrypt(id, json.writeValueAsBytes(Map.of("from", properties.sender(),
            "to", to, "subject", subject, "text", text)));
        var now = Timestamp.from(clock.instant());
        database.update("""
            INSERT INTO email_deliveries(id,owner_scope,owner_identity,tenant_id,location_id,encrypted_payload,payload_nonce,created_at,next_attempt_at)
            VALUES (?,kairos_security.setting('scope'),kairos_security.setting('identity'),
                (SELECT a.tenant_id FROM accounts a WHERE a.id::text=kairos_security.setting('identity') AND kairos_security.setting('scope')='staff'),
                ?,
                ?,?,?,?)
            """, id, locationId, payload.ciphertext(), payload.nonce(), now, now);
        return id;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public EmailDeliveryState state(UUID id) {
        return EmailDeliveryState.valueOf(database.queryForObject("SELECT state FROM email_deliveries WHERE id=?", String.class, id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UUID id) {
        database.update("""
            UPDATE email_deliveries SET state='FAILED',encrypted_payload=NULL,payload_nonce=NULL,
                completed_at=?,claim_token=NULL,claim_until=NULL WHERE id=? AND state='QUEUED'
            """, Timestamp.from(clock.instant()), id);
    }
}
