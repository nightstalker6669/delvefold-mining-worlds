package com.nightsta69.delvefold.audit;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, ordered handoff from gameplay callers to the audit filesystem writer.
 *
 * <p>The single daemon worker is the only thread that may call the sink. Shutdown stops accepting new entries, drains
 * every accepted entry in FIFO order, flushes it, and only then closes it.
 */
final class AuditWriteQueue implements AutoCloseable {
    static final int DEFAULT_CAPACITY = 2_048;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    private final Sink sink;
    private final FailureHandler failures;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Object closeLock = new Object();
    private boolean closed;

    AuditWriteQueue(RotatingAuditLog log, FailureHandler failures) {
        this(new RotatingLogSink(log), DEFAULT_CAPACITY, productionThreadFactory(), failures);
    }

    AuditWriteQueue(Sink sink, int capacity, ThreadFactory threadFactory, FailureHandler failures) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.failures = Objects.requireNonNull(failures, "failures");
        if (capacity < 1) {
            throw new IllegalArgumentException("Audit queue capacity must be positive");
        }
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                Objects.requireNonNull(threadFactory, "threadFactory"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Offers an immutable mutation without waiting for filesystem I/O. */
    boolean offer(AuditMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (!accepting.get()) {
            failures.onFailure("writer_unavailable", null);
            return false;
        }
        try {
            executor.execute(() -> appendContained(mutation));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            failures.onFailure(accepting.get() ? "queue_full" : "writer_unavailable", null);
            return false;
        }
    }

    boolean accepting() {
        return accepting.get();
    }

    private void appendContained(AuditMutation mutation) {
        try {
            sink.append(mutation);
        } catch (Exception exception) {
            failures.onFailure("append_failed", exception);
        }
    }

    /** Synchronously drains all accepted entries, flushes, and closes the sink. */
    @Override
    public void close() {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            accepting.set(false);
            executor.shutdown();
            boolean interrupted = false;
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            try {
                sink.flush();
            } catch (Exception exception) {
                failures.onFailure("flush_failed", exception);
            }
            try {
                sink.close();
            } catch (Exception exception) {
                failures.onFailure("close_failed", exception);
            } finally {
                closed = true;
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static ThreadFactory productionThreadFactory() {
        return task -> {
            Thread thread = new Thread(task, "Delvefold Audit Writer " + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    interface Sink extends AutoCloseable {
        void append(AuditMutation mutation) throws Exception;

        void flush() throws Exception;

        @Override
        void close() throws Exception;
    }

    @FunctionalInterface
    interface FailureHandler {
        void onFailure(String reason, Throwable failure);
    }

    private static final class RotatingLogSink implements Sink {
        private final RotatingAuditLog log;

        private RotatingLogSink(RotatingAuditLog log) {
            this.log = Objects.requireNonNull(log, "log");
        }

        @Override
        public void append(AuditMutation mutation) throws IOException {
            log.append(mutation);
        }

        @Override
        public void flush() throws IOException {
            log.flush();
        }

        @Override
        public void close() throws IOException {
            log.close();
        }
    }
}
