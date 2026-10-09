package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

public abstract class RedisListenerIsolatedIntegrationTest {
    @MockitoBean
    protected ZitadelClient identityProvider;

    @BeforeEach
    void rejectUnconfiguredProviderCalls() {
        PostgresTestDatabase.ownerDatabase().execute("TRUNCATE tenants,customer_push_subscriptions,spring_session RESTART IDENTITY CASCADE");
        Mockito.when(identityProvider.signIn(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenThrow(new InvalidLoginException());
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
