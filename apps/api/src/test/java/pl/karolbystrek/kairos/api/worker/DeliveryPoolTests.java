package pl.karolbystrek.kairos.api.worker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPoolTests {

    @Test
    void aSlowBatchDoesNotBlockTheNextClaimButTwoFullBatchesDo() throws Exception {
        var pool = new DeliveryPool(2);
        var release = new CountDownLatch(1);
        var claims = new AtomicInteger();
        var blocking = (java.util.function.Consumer<Integer>) item -> {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        assertThat(pool.dispatch(() -> List.of(claims.incrementAndGet(), 0), blocking)).isTrue();
        assertThat(pool.dispatch(() -> List.of(claims.incrementAndGet(), 0), blocking)).isTrue();
        assertThat(pool.dispatch(() -> List.of(claims.incrementAndGet()), blocking)).isFalse();
        assertThat(claims).hasValue(2);

        release.countDown();
        var delivered = new CountDownLatch(1);
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!pool.dispatch(() -> List.of(1), item -> delivered.countDown()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
