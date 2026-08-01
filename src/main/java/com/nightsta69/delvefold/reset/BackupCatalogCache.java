package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Bounded, asynchronous view of backup catalogs for tick-thread GUI callers. Callers only normalize a save path; all
 * catalog parsing happens on the daemon worker.
 */
public final class BackupCatalogCache {
    private static final System.Logger LOGGER = System.getLogger(BackupCatalogCache.class.getName());
    private static final long DEFAULT_TTL_MILLIS = 5_000L;
    private static final int DEFAULT_MAX_SAVES = 16;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new WorkerThreadFactory());
    private static final BackupCatalogCache INSTANCE = new BackupCatalogCache(
            WORKER,
            Clock.systemUTC(),
            DEFAULT_TTL_MILLIS,
            DEFAULT_MAX_SAVES,
            saveRoot -> new WorldBackupCatalog(saveRoot).list());

    private final Object lock = new Object();
    private final Executor executor;
    private final Clock clock;
    private final long ttlMillis;
    private final int maxSaves;
    private final CatalogLoader loader;
    private final Map<Path, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<DeleteKey, CompletableFuture<Boolean>> deletes = new HashMap<>();
    private long clearGeneration;

    public static BackupCatalogCache get() {
        return INSTANCE;
    }

    BackupCatalogCache(Executor executor, Clock clock, long ttlMillis, int maxSaves, CatalogLoader loader) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttlMillis < 1L) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        if (maxSaves < 1) {
            throw new IllegalArgumentException("maxSaves must be positive");
        }
        this.ttlMillis = ttlMillis;
        this.maxSaves = maxSaves;
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /** Returns immediately, scheduling a refresh when the cached value is absent or stale. */
    public Snapshot snapshot(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Entry entry = entryLocked(key);
            long age = clock.millis() - entry.loadedAtMillis;
            if (!entry.refreshing && (entry.loadedAtMillis == Long.MIN_VALUE || age >= ttlMillis || age < 0L)) {
                scheduleLocked(key, entry);
            }
            return view(entry);
        }
    }

    /** Starts a refresh now, deduplicating against an existing refresh for the same save. */
    public CompletableFuture<Snapshot> refresh(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Entry entry = entryLocked(key);
            return scheduleLocked(key, entry);
        }
    }

    /** Marks cached data stale and guarantees a refresh after any current refresh completes. */
    public CompletableFuture<Snapshot> invalidateAndRefresh(Path saveRoot) {
        Path key = normalize(saveRoot);
        synchronized (lock) {
            Entry entry = entryLocked(key);
            entry.loadedAtMillis = Long.MIN_VALUE;
            if (entry.refreshing) {
                entry.refreshAgain = true;
                CompletableFuture<Snapshot> current = entry.inFlight;
                return current.thenCompose(ignored -> refresh(key));
            }
            return scheduleLocked(key, entry);
        }
    }

    /**
     * Captures protected references on the server thread, then performs the potentially large deletion walk on the
     * bounded daemon worker and refreshes the catalog.
     */
    public CompletableFuture<Boolean> deleteAndRefreshAsync(MinecraftServer server, String backupId) {
        Objects.requireNonNull(server, "server");
        BackupDeletionGuard.Reservation reservation;
        try {
            reservation = BackupDeletionGuard.get().reserve(server, backupId);
        } catch (IOException | RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return deleteAndRefreshAsync(server.getWorldPath(LevelResource.ROOT), backupId, reservation);
    }

    /** Test seam supplied with an already captured deletion reservation. */
    CompletableFuture<Boolean> deleteAndRefreshAsync(
            Path saveRoot, String backupId, BackupDeletionGuard.Reservation reservation) {
        Path key = normalize(saveRoot);
        DeleteKey deleteKey = new DeleteKey(key, backupId == null ? "" : backupId);
        Objects.requireNonNull(reservation, "reservation");
        if (!reservation.matches(key, deleteKey.backupId())) {
            reservation.close();
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Backup deletion reservation does not match its target"));
        }
        synchronized (lock) {
            CompletableFuture<Boolean> existing = deletes.get(deleteKey);
            if (existing != null) {
                reservation.close();
                return existing;
            }
            if (deletes.size() >= 32) {
                reservation.close();
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Too many backup deletions are already queued"));
            }
            CompletableFuture<Boolean> created = new CompletableFuture<>();
            deletes.put(deleteKey, created);
            long submittedGeneration = clearGeneration;
            try {
                executor.execute(() -> delete(deleteKey, submittedGeneration, reservation, created));
            } catch (RuntimeException failure) {
                deletes.remove(deleteKey, created);
                reservation.close();
                created.completeExceptionally(failure);
            }
            return created;
        }
    }

    /** Drops all per-save state. In-progress tasks cannot republish after this call. */
    public void clear() {
        synchronized (lock) {
            entries.clear();
            deletes.clear();
            clearGeneration++;
        }
    }

    int cachedSaveCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private Entry entryLocked(Path key) {
        Entry entry = entries.get(key);
        if (entry != null) {
            return entry;
        }
        entry = new Entry();
        entries.put(key, entry);
        trimLocked();
        return entry;
    }

    private void trimLocked() {
        Iterator<Map.Entry<Path, Entry>> iterator = entries.entrySet().iterator();
        while (entries.size() > maxSaves && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private CompletableFuture<Snapshot> scheduleLocked(Path key, Entry entry) {
        if (entry.refreshing) {
            return entry.inFlight;
        }
        entry.refreshing = true;
        entry.refreshAgain = false;
        CompletableFuture<Snapshot> future = new CompletableFuture<>();
        entry.inFlight = future;
        try {
            executor.execute(() -> load(key, entry, future));
        } catch (RuntimeException failure) {
            entry.refreshing = false;
            entry.inFlight = null;
            entry.lastError = safeMessage(failure);
            entry.loadedAtMillis = clock.millis();
            future.complete(view(entry));
        }
        return future;
    }

    private void load(Path key, Entry entry, CompletableFuture<Snapshot> future) {
        List<WorldBackupCatalog.BackupSummary> loaded = null;
        String failure = null;
        try {
            loaded = List.copyOf(loader.load(key));
        } catch (IOException | RuntimeException exception) {
            failure = safeMessage(exception);
        }

        Snapshot published;
        synchronized (lock) {
            if (entries.get(key) != entry) {
                published = new Snapshot(List.of(), false, "");
            } else {
                if (loaded != null) {
                    entry.backups = loaded;
                    entry.lastError = "";
                } else {
                    entry.lastError = failure;
                }
                entry.loadedAtMillis = clock.millis();
                entry.refreshing = false;
                entry.inFlight = null;
                boolean refreshAgain = entry.refreshAgain;
                entry.refreshAgain = false;
                if (refreshAgain) {
                    scheduleLocked(key, entry);
                }
                published = view(entry);
            }
        }
        future.complete(published);
    }

    private void delete(
            DeleteKey key,
            long submittedGeneration,
            BackupDeletionGuard.Reservation reservation,
            CompletableFuture<Boolean> future) {
        boolean deleted = false;
        Throwable deleteFailure = null;
        try {
            if (!reservation.permitImmediatelyBeforeDelete()) {
                throw new BackupDeletionGuard.DeletionRejectedException(
                        key.backupId(), BackupDeletionGuard.Reason.SESSION_CLOSED);
            }
            deleted = new WorldBackupCatalog(key.saveRoot()).delete(key.backupId());
        } catch (IOException | RuntimeException failure) {
            deleteFailure = failure;
        }
        boolean cleared;
        synchronized (lock) {
            cleared = clearGeneration != submittedGeneration;
            if (cleared) {
                deletes.remove(key, future);
            }
        }
        if (cleared) {
            reservation.close();
            if (deleteFailure == null) {
                future.complete(deleted);
            } else {
                future.completeExceptionally(deleteFailure);
            }
            return;
        }
        boolean completedDelete = deleted;
        Throwable completedFailure = deleteFailure;
        invalidateAndRefresh(key.saveRoot()).whenComplete((ignored, refreshFailure) -> {
            synchronized (lock) {
                deletes.remove(key, future);
            }
            reservation.close();
            if (completedFailure != null) {
                future.completeExceptionally(completedFailure);
            } else if (refreshFailure != null) {
                future.completeExceptionally(refreshFailure);
            } else {
                future.complete(completedDelete);
            }
        });
    }

    private static Snapshot view(Entry entry) {
        return new Snapshot(entry.backups, entry.refreshing, entry.lastError);
    }

    private static Path normalize(Path saveRoot) {
        return Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
    }

    private static String safeMessage(Exception exception) {
        LOGGER.log(System.Logger.Level.WARNING, "Could not refresh the Delvefold backup catalog", exception);
        return AdminLocalizedMessage.encode("message.delvefold.backup_catalog.refresh_failed");
    }

    @FunctionalInterface
    interface CatalogLoader {
        List<WorldBackupCatalog.BackupSummary> load(Path saveRoot) throws IOException;
    }

    public record Snapshot(List<WorldBackupCatalog.BackupSummary> backups, boolean refreshing, String lastError) {
        public Snapshot {
            backups = List.copyOf(backups);
            lastError = lastError == null ? "" : lastError;
        }
    }

    private static final class Entry {
        private List<WorldBackupCatalog.BackupSummary> backups = List.of();
        private long loadedAtMillis = Long.MIN_VALUE;
        private boolean refreshing;
        private boolean refreshAgain;
        private String lastError = "";
        private CompletableFuture<Snapshot> inFlight;
    }

    private record DeleteKey(Path saveRoot, String backupId) {}

    private static final class WorkerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "delvefold-backup-catalog-" + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
