package com.nightsta69.delvefold.internal.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Creates consistently named daemon threads for Delvefold's independent background executors.
 *
 * <p>Each factory owns its sequence, beginning at one. A factory created without an explicit uncaught-exception handler
 * leaves the thread's inherited JVM handling behavior unchanged.
 */
public final class NamedDaemonThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final @Nullable UncaughtExceptionHandler exceptionHandler;
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * Creates a factory that retains the JVM's default uncaught-exception handling behavior.
     *
     * @param namePrefix exact prefix placed before each factory-local sequence number
     * @throws NullPointerException if {@code namePrefix} is {@code null}
     */
    public NamedDaemonThreadFactory(String namePrefix) {
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix");
        this.exceptionHandler = null;
    }

    /**
     * Creates a factory that installs an explicit uncaught-exception handler on every thread.
     *
     * @param namePrefix exact prefix placed before each factory-local sequence number
     * @param exceptionHandler handler installed directly on every created thread
     * @throws NullPointerException if either argument is {@code null}
     */
    public NamedDaemonThreadFactory(String namePrefix, Thread.UncaughtExceptionHandler exceptionHandler) {
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    /**
     * Creates the next daemon thread in this factory's sequence.
     *
     * @param task work the thread executes when started
     * @return a new, unstarted daemon thread
     */
    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, namePrefix + sequence.incrementAndGet());
        thread.setDaemon(true);
        if (exceptionHandler != null) {
            thread.setUncaughtExceptionHandler(exceptionHandler);
        }
        return thread;
    }
}
