package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.notification.infrastructure.worker.CustomerPushWorker;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker.WebhookWorker;

import java.time.Duration;

@Import(MutableTestClockConfiguration.class)
public abstract class RedisListenerIsolatedIntegrationTest {
    // Fixtures explicitly run durable workers; background commit listeners would race their assertions.
    @MockitoBean
    private CustomerPushWorker customerPushWorker;

    @MockitoBean
    private WebhookWorker webhookWorker;

    @MockitoSpyBean
    protected StaffIdentityStore identities;

    @Autowired
    private MutableTestClock testClock;

    @BeforeEach
    void resetDatabase() {
        testClock.useSystemTime();
        PostgresTestDatabase.ownerDatabase().execute("TRUNCATE tenants,customer_push_subscriptions,spring_session,staff_identities RESTART IDENTITY CASCADE");
    }

    // Verification codes are random; tests replace the stored hash with a known code.
    protected static void useVerificationCode(String email, String code) {
        PostgresTestDatabase.ownerDatabase().update("""
            UPDATE staff_identities SET verification_code_hash=encode(sha256(convert_to(subject || ':' || ?,'UTF8')),'hex'),
                verification_code_expires_at=CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE email=?
            """, code, email);
    }

    // SSE snapshots are written by per-connection workers after the request thread returns.
    protected static String awaitStreamContent(MockHttpServletResponse response, String expected) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!response.getContentAsString().contains(expected) && System.nanoTime() < deadline) Thread.sleep(10);
        return response.getContentAsString();
    }

    @TestBean(enforceOverride = true)
    private RedisMessageListenerContainer orderStatusRedisListenerContainer;

    private static RedisMessageListenerContainer orderStatusRedisListenerContainer() {
        return new NoOpRedisMessageListenerContainer();
    }

    private static final class NoOpRedisMessageListenerContainer
            extends RedisMessageListenerContainer {

        @Override
        public void afterPropertiesSet() {
        }

        @Override
        public void start() {
        }
    }
}
