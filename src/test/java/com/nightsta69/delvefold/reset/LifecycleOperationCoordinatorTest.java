package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LifecycleOperationCoordinatorTest {
    @Test
    void eachOperationKindRejectsTheOtherKindsDraftOrJournal() {
        assertFalse(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.WORLD_OPERATION, false, false));
        assertTrue(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.WORLD_OPERATION, false, true));
        assertFalse(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.WORLD_OPERATION, true, false));

        assertFalse(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.RESTORE, false, false));
        assertTrue(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.RESTORE, true, false));
        assertFalse(LifecycleOperationCoordinator.conflicts(
                LifecycleOperationCoordinator.Kind.RESTORE, false, true));
    }

    @Test
    @Timeout(5)
    void lifecycleTransitionsUseOneOuterLock() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> LifecycleOperationCoordinator.coordinate(() -> {
                firstEntered.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            var second = workers.submit(() -> {
                secondStarted.countDown();
                LifecycleOperationCoordinator.coordinate(secondEntered::countDown);
            });
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS),
                    "A second lifecycle transition entered before the first released the coordinator");

            releaseFirst.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void bothServicesCheckCrossStateAtRequestAndConfirmationBoundaries() throws IOException {
        String world = source("WorldOperationService.java");
        String restore = source("WorldRestoreService.java");

        assertTrue(world.contains("return LifecycleOperationCoordinator.coordinate(() -> requestCoordinated"));
        assertTrue(world.contains("return LifecycleOperationCoordinator.coordinate(() -> confirmCoordinated"));
        assertTrue(world.contains("LifecycleOperationCoordinator.Kind.WORLD_OPERATION"));
        assertTrue(world.indexOf("LifecycleOperationCoordinator.Kind.WORLD_OPERATION")
                < world.indexOf("draft = new Draft"));

        assertTrue(restore.contains("() -> LifecycleOperationCoordinator.coordinate("));
        assertTrue(restore.contains("return LifecycleOperationCoordinator.coordinate(() -> confirmCoordinated"));
        assertTrue(restore.contains("LifecycleOperationCoordinator.Kind.RESTORE"));
        assertTrue(restore.indexOf("LifecycleOperationCoordinator.Kind.RESTORE")
                < restore.indexOf("draft = new Draft"));
    }

    @Test
    void snapshotProducerClassifiesBothPersistedJournalsWithoutCollapsingThem() throws IOException {
        String service = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/admin/DefaultDelvefoldAdminService.java"));

        assertTrue(service.contains("AdminSnapshot.PendingOperation.resolve("));
        assertTrue(service.contains("WorldOperationService.get().hasPending(player.getServer())"));
        assertTrue(service.contains("WorldRestoreService.get().hasPending(player.getServer())"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/reset").resolve(name));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for coordinated test transition");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for coordinated test transition", exception);
        }
    }
}
