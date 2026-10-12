package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;
import pl.karolbystrek.kairos.api.notification.infrastructure.worker.CustomerPushWorker;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker.WebhookWorker;

import java.time.Duration;

public abstract class RedisListenerIsolatedIntegrationTest {
    // Fixtures explicitly run durable workers; background commit listeners would race their assertions.
    @MockitoBean
    private CustomerPushWorker customerPushWorker;

    @MockitoBean
    private WebhookWorker webhookWorker;

    @MockitoBean
    protected ZitadelClient identityProvider;

    @BeforeEach
    void rejectUnconfiguredProviderCalls() {
        PostgresTestDatabase.ownerDatabase().execute("TRUNCATE tenants,customer_push_subscriptions,spring_session RESTART IDENTITY CASCADE");
        Mockito.when(identityProvider.signIn(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenThrow(new InvalidLoginException());
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
