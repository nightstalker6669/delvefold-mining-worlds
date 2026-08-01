package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background API for backup hashing. Its default executor uses daemon worker threads, so command and GUI callers never
 * perform large hashes on a tick thread.
 */
public final class BackupVerificationService {
    private static final System.Logger LOGGER = System.getLogger(BackupVerificationService.class.getName());
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new WorkerThreadFactory());
    private static final int MAX_CACHED_SAVES = 16;
    private static final Map<Path, BackupVerificationService> SERVICES = new LinkedHashMap<>(16, 0.75F, true);

    private final Path saveRoot;
    private final Executor executor;
    private final Clock clock;
    private final Object schedulingLock = new Object();
    private final Map<RequestKey, CompletableFuture<BackupVerificationResult>> inFlight = new HashMap<>();
    private final Map<String, CompletableFuture<BackupVerificationResult>> tails = new HashMap<>();

    public BackupVerificationService(Path saveRoot) {
        this(saveRoot, EXECUTOR, Clock.systemUTC());
    }

    /** Shared per-save instance used by command and GUI handlers. */
    public static BackupVerificationService forSave(Path saveRoot) {
        Path normalized =
                Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
        synchronized (SERVICES) {
            BackupVerificationService existing = SERVICES.get(normalized);
            if (existing != null) {
                return existing;
            }
            pruneIdleServices();
            BackupVerificationService created = new BackupVerificationService(normalized);
            if (SERVICES.size() < MAX_CACHED_SAVES) {
                SERVICES.put(normalized, created);
            }
            return created;
        }
    }

    private static void pruneIdleServices() {
        if (SERVICES.size() < MAX_CACHED_SAVES) {
            return;
        }
        Iterator<Map.Entry<Path, BackupVerificationService>> entries =
                SERVICES.entrySet().iterator();
        while (entries.hasNext() && SERVICES.size() >= MAX_CACHED_SAVES) {
            if (!entries.next().getValue().hasInFlightWork()) {
                entries.remove();
            }
        }
    }

    BackupVerificationService(Path saveRoot, Executor executor, Clock clock) {
        this.saveRoot =
                Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Verifies a manifest backup. Legacy backups are never upgraded implicitly. */
    public CompletableFuture<BackupVerificationResult> verifyAsync(String backupId) {
        return submit(backupId, Action.VERIFY);
    }

    /**
     * Explicitly validates a legacy backup, creates its manifest, and verifies all hashes. No manifest is written
     * unless the legacy layout is valid.
     */
    public CompletableFuture<BackupVerificationResult> validateLegacyAndCreateManifestAsync(String backupId) {
        return submit(backupId, Action.UPGRADE_LEGACY);
    }

    public boolean isInFlight(String backupId) {
        synchronized (schedulingLock) {
            return inFlight.keySet().stream().anyMatch(key -> key.backupId().equals(backupId));
        }
    }

    private CompletableFuture<BackupVerificationResult> submit(String suppliedId, Action action) {
        String backupId = suppliedId == null ? "" : suppliedId;
        RequestKey key = new RequestKey(backupId, action);
        CompletableFuture<BackupVerificationResult> predecessor;
        CompletableFuture<BackupVerificationResult> created;
        synchronized (schedulingLock) {
            CompletableFuture<BackupVerificationResult> existing = inFlight.get(key);
            if (existing != null) {
                return existing;
            }
            predecessor = tails.get(backupId);
            created = new CompletableFuture<>();
            inFlight.put(key, created);
            tails.put(backupId, created);
        }
        Runnable schedule = () -> execute(key, backupId, action, created);
        if (predecessor == null) {
            schedule.run();
        } else {
            predecessor.whenComplete((ignored, failure) -> schedule.run());
        }
        return created;
    }

    private void execute(
            RequestKey key, String backupId, Action action, CompletableFuture<BackupVerificationResult> created) {
        try {
            executor.execute(() -> {
                try {
                    created.complete(run(backupId, action));
                } catch (Throwable failure) {
                    created.completeExceptionally(failure);
                } finally {
                    finish(key, backupId, created);
                }
            });
        } catch (RuntimeException failure) {
            created.completeExceptionally(failure);
            finish(key, backupId, created);
        }
    }

    private void finish(RequestKey key, String backupId, CompletableFuture<BackupVerificationResult> completed) {
        synchronized (schedulingLock) {
            inFlight.remove(key, completed);
            tails.remove(backupId, completed);
        }
    }

    private boolean hasInFlightWork() {
        synchronized (schedulingLock) {
            return !inFlight.isEmpty();
        }
    }

    private BackupVerificationResult run(String backupId, Action action) {
        long started = clock.millis();
        String worker = Thread.currentThread().getName();
        try {
            WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
            Path root = catalog.resolve(backupId);
            BackupManifestService manifests = new BackupManifestService(clock);
            if (!BackupManifestService.hasManifest(root)) {
                if (action == Action.VERIFY) {
                    return result(
                            backupId,
                            BackupVerificationResult.Status.LEGACY_REQUIRES_VALIDATION,
                            localized("message.delvefold.backup_verification.legacy_requires_validation", backupId),
                            0,
                            sizeOrUnknown(root),
                            started,
                            worker);
                }
                BackupManifestService.Verification verification = manifests.createVerifiedManifest(root);
                return result(
                        backupId,
                        BackupVerificationResult.Status.LEGACY_UPGRADED,
                        localized(
                                "message.delvefold.backup_verification.legacy_upgraded",
                                backupId,
                                verification.receipt().fileCount(),
                                verification.receipt().totalBytes()),
                        verification.receipt().fileCount(),
                        verification.receipt().totalBytes(),
                        started,
                        worker);
            }
            BackupManifestService.Verification verification = manifests.verify(root);
            return result(
                    backupId,
                    BackupVerificationResult.Status.VERIFIED,
                    localized(
                            "message.delvefold.backup_verification.verified",
                            backupId,
                            verification.receipt().fileCount(),
                            verification.receipt().totalBytes()),
                    verification.receipt().fileCount(),
                    verification.receipt().totalBytes(),
                    started,
                    worker);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Backup verification failed for " + backupId, exception);
            return result(
                    backupId,
                    BackupVerificationResult.Status.FAILED,
                    localized("message.delvefold.backup_verification.failed", backupId),
                    0,
                    -1,
                    started,
                    worker);
        }
    }

    private BackupVerificationResult result(
            String id,
            BackupVerificationResult.Status status,
            String message,
            int files,
            long bytes,
            long started,
            String worker) {
        return new BackupVerificationResult(id, status, message, files, bytes, started, clock.millis(), worker);
    }

    private static long sizeOrUnknown(Path root) {
        long total = 0L;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                total = Math.addExact(total, Files.size(path));
            }
            return total;
        } catch (IOException | ArithmeticException exception) {
            return -1L;
        }
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private enum Action {
        VERIFY,
        UPGRADE_LEGACY
    }

    private record RequestKey(String backupId, Action action) {}

    private static final class WorkerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Delvefold Backup Verifier " + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, failure) -> {
                // CompletableFuture captures task failures; this only protects the executor itself.
            });
            return thread;
        }
    }
}
