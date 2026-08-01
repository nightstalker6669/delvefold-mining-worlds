package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupCatalogCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void firstMissReturnsImmediatelyAndDeduplicatesNormalizedSaveRoot() {
        ManualExecutor executor = new ManualExecutor();
        MutableClock clock = new MutableClock();
        AtomicInteger loads = new AtomicInteger();
        WorldBackupCatalog.BackupSummary summary = summary("first");
        BackupCatalogCache cache = new BackupCatalogCache(executor, clock, 1_000L, 16, ignored -> {
            loads.incrementAndGet();
            return List.of(summary);
        });

        BackupCatalogCache.Snapshot first = cache.snapshot(Path.of("save", "..", "save"));
        BackupCatalogCache.Snapshot repeated = cache.snapshot(Path.of("save"));

        assertTrue(first.refreshing());
        assertTrue(first.backups().isEmpty());
        assertTrue(repeated.refreshing());
        assertEquals(1, executor.size());
        assertEquals(0, loads.get(), "the caller thread must not invoke the catalog loader");

        executor.runNext();
        BackupCatalogCache.Snapshot loaded = cache.snapshot(Path.of("save"));
        assertFalse(loaded.refreshing());
        assertEquals(List.of(summary), loaded.backups());
        assertThrows(UnsupportedOperationException.class, () -> loaded.backups().add(summary("other")));
    }

    @Test
    void staleSnapshotsRemainAvailableWhileOneBackgroundRefreshRuns() {
        ManualExecutor executor = new ManualExecutor();
        MutableClock clock = new MutableClock();
        AtomicInteger loads = new AtomicInteger();
        BackupCatalogCache cache = new BackupCatalogCache(executor, clock, 100L, 16,
                ignored -> List.of(summary("v" + loads.incrementAndGet())));

        cache.snapshot(Path.of("save"));
        executor.runNext();
        clock.advanceMillis(100L);

        BackupCatalogCache.Snapshot stale = cache.snapshot(Path.of("save"));
        BackupCatalogCache.Snapshot duplicate = cache.snapshot(Path.of("save"));
        assertEquals("v1", stale.backups().getFirst().id());
        assertTrue(stale.refreshing());
        assertTrue(duplicate.refreshing());
        assertEquals(1, executor.size());

        executor.runNext();
        assertEquals("v2", cache.snapshot(Path.of("save")).backups().getFirst().id());
    }

    @Test
    void invalidationDuringRefreshGuaranteesASecondRefresh() {
        ManualExecutor executor = new ManualExecutor();
        MutableClock clock = new MutableClock();
        AtomicInteger loads = new AtomicInteger();
        BackupCatalogCache cache = new BackupCatalogCache(executor, clock, 10_000L, 16,
                ignored -> List.of(summary("v" + loads.incrementAndGet())));
        Path root = Path.of("save");
        cache.snapshot(root);
        executor.runNext();

        var current = cache.refresh(root);
        var afterInvalidation = cache.invalidateAndRefresh(root);
        assertSame(current, cache.refresh(root));
        executor.runNext();
        assertEquals(1, executor.size(), "an invalidation must queue a post-mutation refresh");
        executor.runNext();

        assertEquals("v3", afterInvalidation.join().backups().getFirst().id());
        assertFalse(cache.snapshot(root).refreshing());
    }

    @Test
    void cacheBoundsSavesAndClearPreventsLatePublication() {
        ManualExecutor executor = new ManualExecutor();
        BackupCatalogCache cache = new BackupCatalogCache(executor, new MutableClock(), 1_000L, 3,
                ignored -> List.of(summary("loaded")));

        for (int index = 0; index < 8; index++) {
            cache.snapshot(Path.of("save-" + index));
        }
        assertEquals(3, cache.cachedSaveCount());

        cache.clear();
        executor.runAll();
        assertEquals(0, cache.cachedSaveCount());
        assertTrue(cache.snapshot(Path.of("save-7")).backups().isEmpty());
    }

    @Test
    void deletionWalkRunsOnExecutorAndCompletesAfterRefresh() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        BackupCatalogCache cache = new BackupCatalogCache(executor, new MutableClock(), 1_000L, 16,
                ignored -> List.of());
        Path backup = temporaryDirectory.resolve("delvefold_backups/to-delete");
        Files.createDirectories(backup);
        Files.writeString(backup.resolve("large-region-file"), "data");

        var reservation = BackupDeletionGuard.get().reserveForTest(
                temporaryDirectory, "to-delete", Set.of());
        var deletion = cache.deleteAndRefreshAsync(temporaryDirectory, "to-delete", reservation);
        assertTrue(Files.exists(backup), "the caller thread must not start walking the backup");
        assertFalse(deletion.isDone());

        executor.runNext();
        assertFalse(Files.exists(backup));
        assertFalse(deletion.isDone(), "completion waits for the refreshed immutable catalog");
        executor.runNext();
        assertTrue(deletion.join());
        assertTrue(BackupDeletionGuard.get().coordinateReferenceForTest(
                temporaryDirectory, "to-delete", () -> { }));
    }

    @Test
    void revokedReservationPreventsAQueuedDeletionWalk() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        BackupCatalogCache cache = new BackupCatalogCache(executor, new MutableClock(), 1_000L, 16,
                ignored -> List.of());
        Path backup = temporaryDirectory.resolve("delvefold_backups/kept");
        Files.createDirectories(backup);
        Files.writeString(backup.resolve("large-region-file"), "data");
        var reservation = BackupDeletionGuard.get().reserveForTest(
                temporaryDirectory, "kept", Set.of());

        var deletion = cache.deleteAndRefreshAsync(temporaryDirectory, "kept", reservation);
        reservation.close();
        executor.runAll();

        assertTrue(Files.exists(backup),
                "the worker must recheck its reservation immediately before filesystem deletion");
        assertTrue(deletion.isCompletedExceptionally());
    }

    private static WorldBackupCatalog.BackupSummary summary(String id) {
        return new WorldBackupCatalog.BackupSummary(
                id, 1L, "delete", "flat", 2L, false, true, true,
                true, true, false);
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
