package pl.karolbystrek.kairos.api.worker;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs claimed deliveries on virtual threads without waiting for the batch, so one slow recipient cannot hold up
 * the next claim. At most two batches are in flight; a claim waits until a whole batch fits.
 */
public final class DeliveryPool {

    private final int batchSize;
    private final Semaphore slots;

    public DeliveryPool(int batchSize) {
        this.batchSize = batchSize;
        slots = new Semaphore(2 * batchSize);
    }

    /** Must not run concurrently with itself; the caller's coalescing worker guarantees that. */
    public <T> boolean dispatch(Supplier<List<T>> claim, Consumer<T> deliver) {
        if (slots.availablePermits() < batchSize) return false;
        var claimed = claim.get();
        claimed.forEach(item -> {
            slots.acquireUninterruptibly();
            Thread.startVirtualThread(() -> {
                try {
                    deliver.accept(item);
                } finally {
                    slots.release();
                }
            });
        });
        return !claimed.isEmpty();
    }
}
