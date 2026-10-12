package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class SseConnection {
    private record Pending(SseEmitter.SseEventBuilder event, boolean terminal) {}

    final SseEmitter emitter;
    private final Runnable remove;
    private final AtomicReference<Pending> pending = new AtomicReference<>();
    private final AtomicBoolean heartbeat = new AtomicBoolean();
    private final CoalescingWorker writer;
    private volatile boolean closed;

    SseConnection(SseEmitter emitter, Runnable remove, Executor executor) {
        this.emitter = emitter;
        this.remove = remove;
        writer = new CoalescingWorker(executor, this::write);
        emitter.onCompletion(this::removed);
        emitter.onTimeout(this::removed);
        emitter.onError(ignored -> removed());
    }

    void send(SseEmitter.SseEventBuilder event, boolean terminal) {
        if (closed) return;
        pending.set(new Pending(event, terminal));
        writer.wake();
    }

    void heartbeat() {
        heartbeat.set(true);
        writer.wake();
    }

    void close() {
        removed();
        emitter.complete();
    }

    private void removed() {
        closed = true;
        remove.run();
    }

    private boolean write() {
        if (closed) return false;
        var next = pending.getAndSet(null);
        var sendHeartbeat = heartbeat.getAndSet(false);
        try {
            if (next != null) {
                emitter.send(next.event());
                if (next.terminal()) close();
            } else if (sendHeartbeat) {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            }
        } catch (IOException | RuntimeException exception) {
            removed();
        }
        return false;
    }
}
