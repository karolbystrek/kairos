package pl.karolbystrek.kairos.api.network;

import lombok.NonNull;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Shared outbound HTTP execution for Web Push and webhooks: every request runs under one hard deadline
 * against a single-use client whose DNS is pinned to the addresses the destination policy validated.
 */
public final class PinnedHttpExecutor {

    private static final int MAXIMUM_RESPONSE_LINE_LENGTH = 8_192;
    private static final int MAXIMUM_RESPONSE_HEADER_COUNT = 100;
    private static final int MAXIMUM_LEADING_EMPTY_LINES = 10;

    private PinnedHttpExecutor() {
    }

    @FunctionalInterface
    public interface Task<T> {
        T execute(Execution execution) throws IOException;
    }

    public static final class Execution {
        private final AtomicBoolean deadlineExceeded = new AtomicBoolean();
        private final AtomicReference<CloseableHttpClient> clientReference = new AtomicReference<>();

        public boolean expired() {
            return deadlineExceeded.get() || Thread.currentThread().isInterrupted();
        }

        /** Returns a client pinned to the validated addresses, or null when the deadline already passed. */
        public CloseableHttpClient open(String host, InetAddress[] addresses, Duration totalTimeout) {
            var client = buildClient(host, addresses, totalTimeout);
            clientReference.set(client);
            if (deadlineExceeded.get()) {
                closeImmediately(client);
                clientReference.compareAndSet(client, null);
                return null;
            }
            return client;
        }

        public void release(CloseableHttpClient client) {
            clientReference.compareAndSet(client, null);
        }
    }

    /**
     * Runs the task on a virtual thread under {@code totalTimeout}. {@code subject} names the operation in
     * failure details and {@code failure} builds the caller's result from an outcome and a detail.
     */
    public static <T> T run(
            Duration totalTimeout,
            String subject,
            BiFunction<String, String, T> failure,
            Task<T> task
    ) {
        var execution = new Execution();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var future = executor.submit(() -> task.execute(execution));
            try {
                return future.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                execution.deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(execution.clientReference.get());
                return failure.apply("TIMEOUT", subject + " exceeded the total timeout");
            } catch (ExecutionException exception) {
                var cause = exception.getCause();
                if (isTimeout(cause)) {
                    return failure.apply("TIMEOUT", subject + " exceeded the total timeout");
                }
                return failure.apply("NETWORK_ERROR", safeMessage(cause, subject + " failed"));
            } catch (InterruptedException exception) {
                execution.deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(execution.clientReference.get());
                Thread.currentThread().interrupt();
                return failure.apply("INTERRUPTED", subject + " was interrupted");
            }
        } finally {
            executor.shutdownNow();
            closeImmediately(execution.clientReference.get());
        }
    }

    public static String safeMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallback;
        }
        var message = throwable.getMessage();
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static CloseableHttpClient buildClient(String host, InetAddress[] addresses, Duration totalTimeout) {
        var timeout = Timeout.of(totalTimeout);
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        var http1Config = Http1Config.custom()
                .setMaxLineLength(MAXIMUM_RESPONSE_LINE_LENGTH)
                .setMaxHeaderCount(MAXIMUM_RESPONSE_HEADER_COUNT)
                .setMaxEmptyLineCount(MAXIMUM_LEADING_EMPTY_LINES)
                .build();
        var connectionFactory = ManagedHttpClientConnectionFactory.builder()
                .http1Config(http1Config)
                .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connectionFactory)
                .setDnsResolver(new PinnedDnsResolver(host, addresses))
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .build();
        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .setRedirectsEnabled(false)
                .setAuthenticationEnabled(false)
                .setContentCompressionEnabled(false)
                .setProtocolUpgradeEnabled(false)
                .setHardCancellationEnabled(true)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionReuseStrategy((request, response, context) -> false)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableConnectionState()
                .disableContentCompression()
                .build();
    }

    private static void closeImmediately(CloseableHttpClient client) {
        if (client != null) {
            client.close(CloseMode.IMMEDIATE);
        }
    }

    private static boolean isTimeout(Throwable throwable) {
        var current = throwable;
        while (current != null) {
            if (current instanceof InterruptedIOException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PinnedDnsResolver(
            @NonNull String host,
            @NonNull InetAddress[] addresses
    ) implements DnsResolver {

        private PinnedDnsResolver {
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
            requireExpectedHost(requestedHost);
            return addresses.clone();
        }

        @Override
        public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
            requireExpectedHost(requestedHost);
            return host;
        }

        private void requireExpectedHost(String requestedHost) throws UnknownHostException {
            if (!host.equalsIgnoreCase(requestedHost)) {
                throw new UnknownHostException("Refusing to resolve an unvalidated destination host");
            }
        }
    }
}
