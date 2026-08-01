package com.nightsta69.delvefold.audit;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Tracks asynchronous filesystem mutations whose audit entry must be queued before a save closes.
 *
 * <p>Normal completion notifications may still return to the server executor, but the small, thread-safe audit callback
 * registered here runs on the completing thread. Server shutdown drains these callbacks before stopping the per-save
 * audit writer, so a completed mutation cannot be silently attributed to the next save or dropped after the writer has
 * closed.
 */
public final class AsyncAuditMutationTracker {
    private static final System.Logger LOGGER = System.getLogger(AsyncAuditMutationTracker.class.getName());
    private static final AsyncAuditMutationTracker INSTANCE = new AsyncAuditMutationTracker();

    private final Object lock = new Object();
    private final Map<Path, Session> sessions = new HashMap<>();

    /** Creates an independent tracker whose save sessions are initially empty and closed. */
    public AsyncAuditMutationTracker() {}

    /**
     * Returns the process-wide tracker.
     *
     * @return tracker whose independent sessions are keyed by normalized save root
     */
    public static AsyncAuditMutationTracker get() {
        return INSTANCE;
    }

    /**
     * Reserves an isolated, initially closed session before a new per-save writer is installed.
     *
     * @param saveRoot save directory normalized to an absolute session key
     * @throws IllegalStateException if an earlier session for the same save was not ended
     */
    public void beginSession(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            if (sessions.containsKey(key)) {
                throw new IllegalStateException("An earlier Delvefold asynchronous-audit session has not ended");
            }
            sessions.put(key, new Session());
        }
    }

    /**
     * Opens the reserved session only after its per-save audit writer is ready.
     *
     * @param saveRoot save directory identifying the reserved session
     * @throws IllegalStateException if no clean reserved session exists
     */
    public void openSession(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session == null || session.accepting || !session.pending.isEmpty()) {
                throw new IllegalStateException("The Delvefold asynchronous-audit session cannot be opened");
            }
            session.accepting = true;
        }
    }

    /**
     * Registers the shutdown gate before invoking {@code sourceFactory}, so closing a save either observes the
     * operation or rejects it before its filesystem mutation can begin.
     *
     * <p>Callback failures are contained because auditing must never roll back the completed mutation. A rejected start
     * is returned as a failed future and never invokes the factory.
     *
     * @param <T> asynchronous operation result type
     * @param saveRoot save directory whose shutdown must wait for the audit callback
     * @param sourceFactory factory invoked exactly once after the shutdown gate is registered
     * @param auditCompletion nonblocking audit callback receiving nullable result or failure on the completing thread
     * @return the source completion as a future, or an already-failed future when admission is closed
     */
    public <T> CompletableFuture<T> startTracked(
            Path saveRoot,
            Supplier<? extends CompletionStage<T>> sourceFactory,
            BiConsumer<? super @Nullable T, ? super @Nullable Throwable> auditCompletion) {
        Path key = normalize(saveRoot);
        Objects.requireNonNull(sourceFactory, "sourceFactory");
        Objects.requireNonNull(auditCompletion, "auditCompletion");
        CompletableFuture<@Nullable Void> gate = new CompletableFuture<>();
        CompletionStage<T> source;
        Session trackedSession;
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session == null || !session.accepting) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("The Delvefold save session is closing"));
            }
            trackedSession = session;
            session.pending.put(gate, Boolean.TRUE);
            try {
                source = Objects.requireNonNull(sourceFactory.get(), "sourceFactory result");
            } catch (RuntimeException exception) {
                session.pending.remove(gate);
                return CompletableFuture.failedFuture(exception);
            }
        }
        CompletionStage<@Nullable Void> unusedGateRemoval =
                gate.whenComplete((ignored, failure) -> remove(key, trackedSession, gate));
        try {
            CompletionStage<T> unusedAuditCompletion = source.whenComplete((result, failure) -> {
                try {
                    auditCompletion.accept(result, failure);
                } catch (RuntimeException exception) {
                    LOGGER.log(System.Logger.Level.ERROR, "Delvefold asynchronous audit callback failed", exception);
                } finally {
                    gate.complete(null);
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Delvefold could not register an asynchronous audit callback",
                    exception);
            gate.complete(null);
        }
        return source.toCompletableFuture();
    }

    /**
     * Atomically prevents any later tracked mutation from invoking its source factory.
     *
     * @param saveRoot save directory whose session is entering shutdown
     */
    public void stopAccepting(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session != null) {
                session.accepting = false;
            }
        }
    }

    /**
     * Waits for every already-registered audit callback for this save to finish.
     *
     * @param saveRoot save directory whose accepted callbacks must drain
     * @throws IllegalStateException if admission has not first been stopped
     */
    public void drain(Path saveRoot) {
        Path key = normalize(saveRoot);
        while (true) {
            List<CompletableFuture<@Nullable Void>> snapshot;
            synchronized (lock) {
                Session session = sessions.get(key);
                if (session == null || session.pending.isEmpty()) {
                    return;
                }
                if (session.accepting) {
                    throw new IllegalStateException(
                            "The Delvefold asynchronous-audit session must close before it drains");
                }
                snapshot = List.copyOf(session.pending.keySet());
            }
            CompletableFuture.allOf(snapshot.toArray(CompletableFuture[]::new)).join();
        }
    }

    /**
     * Removes one fully drained session so a later server can open a distinct generation.
     *
     * @param saveRoot save directory whose completed session should be removed
     * @throws IllegalStateException if the session is still accepting or has pending callbacks
     */
    public void endSession(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session == null) {
                return;
            }
            if (session.accepting || !session.pending.isEmpty()) {
                throw new IllegalStateException(
                        "The Delvefold asynchronous-audit session has not been closed and drained");
            }
            sessions.remove(key, session);
        }
    }

    private void remove(Path key, Session trackedSession, CompletableFuture<@Nullable Void> gate) {
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session != trackedSession) {
                return;
            }
            session.pending.remove(gate);
        }
    }

    private static Path normalize(Path saveRoot) {
        return Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
    }

    private static final class Session {
        private final IdentityHashMap<CompletableFuture<@Nullable Void>, Boolean> pending = new IdentityHashMap<>();
        private boolean accepting;
    }
}
