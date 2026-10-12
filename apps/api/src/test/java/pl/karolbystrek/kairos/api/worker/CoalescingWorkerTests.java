package pl.karolbystrek.kairos.api.worker;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CoalescingWorkerTests {
    @Test
    void wakeupsReturnBeforeProcessingAndCoalesceUntilTheQueueIsDrained() {
        var tasks = new ArrayDeque<Runnable>();
        var cycles = new AtomicInteger();
        var worker = new CoalescingWorker(tasks::add, () -> cycles.incrementAndGet() < 3);
        worker.wake();
        worker.wake();
        assertThat(cycles).hasValue(0);
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(cycles).hasValue(3);
        worker.wake();
        tasks.remove().run();
        assertThat(cycles).hasValue(4);
    }

    @Test
    void wakeupDuringTheLastCycleRunsAgainWithoutStartingAnOverlappingTask() {
        var tasks = new ArrayDeque<Runnable>();
        var cycles = new AtomicInteger();
        var holder = new CoalescingWorker[1];
        holder[0] = new CoalescingWorker(tasks::add, () -> {
            if (cycles.incrementAndGet() == 1) holder[0].wake();
            return false;
        });
        holder[0].wake();
        tasks.remove().run();
        assertThat(cycles).hasValue(2);
        assertThat(tasks).isEmpty();
    }
}
