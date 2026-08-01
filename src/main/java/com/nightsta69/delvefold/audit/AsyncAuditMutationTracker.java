package com.nightsta69.delvefold.audit;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Tracks asynchronous filesystem mutations whose audit entry must be queued before a save closes.
 *
 * <p>Normal completion notifications may still return to the server executor, but the small,
 * thread-safe audit callback registered here runs on the completing thread. Server shutdown drains
 * these callbacks before stopping the per-save audit writer, so a completed mutation cannot be
 * silently attributed to the next save or dropped after the writer has closed.</p>
 */
public final class AsyncAuditMutationTracker {
    private static final System.Logger LOGGER = System.getLogger(AsyncAuditMutationTracker.class.getName());
    private static final AsyncAuditMutationTracker INSTANCE = new AsyncAuditMutationTracker();

    private final Object lock = new Object();
    private final Map<Path, Session> sessions = new HashMap<>();

    public static AsyncAuditMutationTracker get() {
        return INSTANCE;
    }

    /** Reserves an isolated, initially closed session before a new per-save writer is installed. */
    public void beginSession(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            if (sessions.containsKey(key)) {
                throw new IllegalStateException(
                        "An earlier Delvefold asynchronous-audit session has not ended");
            }
            sessions.put(key, new Session());
        }
    }

    /** Opens the reserved session only after its per-save audit writer is ready. */
    public void openSession(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session == null || session.accepting || !session.pending.isEmpty()) {
                throw new IllegalStateException(
                        "The Delvefold asynchronous-audit session cannot be opened");
            }
            session.accepting = true;
        }
    }

    /**
     * Registers the shutdown gate before invoking {@code sourceFactory}, so closing a save either
     * observes the operation or rejects it before its filesystem mutation can begin.
     *
     * <p>Callback failures are contained because auditing must never roll back the completed
     * mutation. A rejected start is returned as a failed future and never invokes the factory.</p>
     */
    public <T> CompletableFuture<T> startTracked(
            Path saveRoot,
            Supplier<? extends CompletionStage<T>> sourceFactory,
            BiConsumer<? super T, ? super Throwable> auditCompletion
    ) {
        Path key = normalize(saveRoot);
        Objects.requireNonNull(sourceFactory, "sourceFactory");
        Objects.requireNonNull(auditCompletion, "auditCompletion");
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletionStage<T> source;
        Session trackedSession;
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session == null || !session.accepting) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "The Delvefold save session is closing"));
            }
            trackedSession = session;
            session.pending.add(gate);
            try {
                source = Objects.requireNonNull(sourceFactory.get(), "sourceFactory result");
            } catch (RuntimeException exception) {
                session.pending.remove(gate);
                return CompletableFuture.failedFuture(exception);
            }
        }
        gate.whenComplete((ignored, failure) -> remove(key, trackedSession, gate));
        try {
            source.whenComplete((result, failure) -> {
                try {
                    auditCompletion.accept(result, failure);
                } catch (RuntimeException exception) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            "Delvefold asynchronous audit callback failed", exception);
                } finally {
                    gate.complete(null);
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Delvefold could not register an asynchronous audit callback", exception);
            gate.complete(null);
        }
        return source.toCompletableFuture();
    }

    /** Atomically prevents any later tracked mutation from invoking its source factory. */
    public void stopAccepting(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Session session = sessions.get(key);
            if (session != null) {
                session.accepting = false;
            }
        }
    }

    /** Waits for every already-registered audit callback for this save to finish. */
    public void drain(Path saveRoot) {
        Path key = normalize(saveRoot);
        while (true) {
            List<CompletableFuture<Void>> snapshot;
            synchronized (lock) {
                Session session = sessions.get(key);
                if (session == null || session.pending.isEmpty()) {
                    return;
                }
                if (session.accepting) {
                    throw new IllegalStateException(
                            "The Delvefold asynchronous-audit session must close before it drains");
                }
                snapshot = List.copyOf(session.pending);
            }
            CompletableFuture.allOf(snapshot.toArray(CompletableFuture[]::new)).join();
        }
    }

    /** Removes one fully drained session so a later server can open a distinct generation. */
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

    private void remove(Path key, Session trackedSession, CompletableFuture<Void> gate) {
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
        private final Set<CompletableFuture<Void>> pending = new LinkedHashSet<>();
        private boolean accepting;
    }
}
