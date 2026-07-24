package pl.karolbystrek.kairos.api.testsupport;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.convention.TestBean;

public abstract class RedisListenerIsolatedIntegrationTest {

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
