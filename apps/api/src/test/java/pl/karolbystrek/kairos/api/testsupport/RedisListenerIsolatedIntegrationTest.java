package pl.karolbystrek.kairos.api.testsupport;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.convention.TestBean;

public abstract class RedisListenerIsolatedIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient identityProvider;

    @org.junit.jupiter.api.BeforeEach
    void rejectUnconfiguredProviderCalls() {
        PostgresTestDatabase.ownerDatabase().execute("TRUNCATE tenants,customer_push_subscriptions,spring_session RESTART IDENTITY CASCADE");
        org.mockito.Mockito.when(identityProvider.signIn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException());
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
