package pl.karolbystrek.kairos.api.testsupport;

import org.testcontainers.containers.GenericContainer;

// Started on first use, so only tests that need a real Redis pay for it; the launcher session listener stops it.
public final class RedisTestContainer {
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    private RedisTestContainer() {}

    public static synchronized void start() {
        if (!REDIS.isRunning()) REDIS.start();
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }

    static synchronized void stop() {
        if (REDIS.isRunning()) REDIS.stop();
    }
}
