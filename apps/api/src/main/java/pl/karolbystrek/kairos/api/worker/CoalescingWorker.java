package pl.karolbystrek.kairos.api.worker;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

public final class CoalescingWorker {

    private final Executor executor;
    private final BooleanSupplier cycle;
    private boolean running;
    private boolean pending;

    public CoalescingWorker(BooleanSupplier cycle) {
        this(Thread::startVirtualThread, cycle);
    }

    public CoalescingWorker(Executor executor, BooleanSupplier cycle) {
        this.executor = executor;
        this.cycle = cycle;
    }

    public synchronized void wake() {
        pending = true;
        if (running) return;
        running = true;
        try {
            executor.execute(this::drain);
        } catch (RuntimeException exception) {
            running = false;
            throw exception;
        }
    }

    private void drain() {
        try {
            while (true) {
                synchronized (this) {
                    pending = false;
                }
                var processed = cycle.getAsBoolean();
                synchronized (this) {
                    if (!processed && !pending) {
                        running = false;
                        return;
                    }
                }
            }
        } catch (RuntimeException | Error exception) {
            synchronized (this) {
                running = false;
                if (pending) wake();
            }
            throw exception;
        }
    }
}
