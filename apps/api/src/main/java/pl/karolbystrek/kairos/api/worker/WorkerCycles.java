package pl.karolbystrek.kairos.api.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;

/** Times worker cycles and counts their failures under the {@code worker} tag. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerCycles {

    private final MeterRegistry meters;

    /** Runs one cycle; a failure is counted, logged and reported as no work done. */
    public boolean run(String worker, String failureMessage, BooleanSupplier cycle) {
        var sample = Timer.start(meters);
        try {
            return cycle.getAsBoolean();
        } catch (RuntimeException exception) {
            recordFailure(worker, failureMessage, exception);
            return false;
        } finally {
            sample.stop(meters.timer("kairos.worker.cycle.duration", "worker", worker));
        }
    }

    public void run(String worker, String failureMessage, Runnable cycle) {
        run(worker, failureMessage, () -> {
            cycle.run();
            return true;
        });
    }

    public void recordFailure(String worker, String message, RuntimeException exception) {
        meters.counter("kairos.worker.failures", "worker", worker).increment();
        log.error(message, exception);
    }
}
