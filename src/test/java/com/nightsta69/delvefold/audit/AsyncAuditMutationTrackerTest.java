package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AsyncAuditMutationTrackerTest {
    private static final Path SAVE_ROOT = Path.of("build/test-saves/async-audit");

    @Test
    void closingRejectsTheSourceBeforeItCanMutateAndKeepsSessionsIsolated() {
        AsyncAuditMutationTracker tracker = new AsyncAuditMutationTracker();
        CompletableFuture<String> oldSource = new CompletableFuture<>();
        AtomicBoolean oldAudited = new AtomicBoolean();
        tracker.beginSession(SAVE_ROOT);
        tracker.openSession(SAVE_ROOT);
        tracker.startTracked(SAVE_ROOT, () -> oldSource,
                (result, failure) -> oldAudited.set(failure == null));

        tracker.stopAccepting(SAVE_ROOT);
        AtomicBoolean lateFactoryInvoked = new AtomicBoolean();
        CompletableFuture<String> rejected = tracker.startTracked(SAVE_ROOT, () -> {
            lateFactoryInvoked.set(true);
            return CompletableFuture.completedFuture("late mutation");
        }, (result, failure) -> { });

        assertThrows(CompletionException.class, rejected::join);
        assertFalse(lateFactoryInvoked.get(), "Closing must reject before the mutation source starts");
        assertThrows(IllegalStateException.class, () -> tracker.beginSession(SAVE_ROOT),
                "A new save session must not replace a pending callback from the old session");

        oldSource.complete("old mutation");
        tracker.drain(SAVE_ROOT);
        assertThrows(IllegalStateException.class, () -> tracker.beginSession(SAVE_ROOT),
                "Even a drained generation remains isolated until lifecycle explicitly ends it");
        tracker.endSession(SAVE_ROOT);
        assertTrue(oldAudited.get());

        AtomicBoolean newAudited = new AtomicBoolean();
        tracker.beginSession(SAVE_ROOT);
        tracker.openSession(SAVE_ROOT);
        tracker.startTracked(SAVE_ROOT, () -> CompletableFuture.completedFuture("new mutation"),
                (result, failure) -> newAudited.set(failure == null));
        tracker.stopAccepting(SAVE_ROOT);
        tracker.drain(SAVE_ROOT);
        tracker.endSession(SAVE_ROOT);
        assertTrue(newAudited.get());
    }

    @Test
    void drainWaitsUntilTheAuditCallbackItselfHasFinished() throws Exception {
        AsyncAuditMutationTracker tracker = new AsyncAuditMutationTracker();
        CompletableFuture<String> source = new CompletableFuture<>();
        CountDownLatch auditEntered = new CountDownLatch(1);
        CountDownLatch releaseAudit = new CountDownLatch(1);
        AtomicBoolean audited = new AtomicBoolean();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        tracker.beginSession(SAVE_ROOT);
        tracker.openSession(SAVE_ROOT);
        tracker.startTracked(SAVE_ROOT, () -> source, (result, failure) -> {
            auditEntered.countDown();
            try {
                releaseAudit.await();
                audited.set(failure == null);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test audit callback was interrupted", exception);
            }
        });

        try {
            Future<?> completion = workers.submit(() -> source.complete("mutation complete"));
            assertTrue(auditEntered.await(5, TimeUnit.SECONDS));
            tracker.stopAccepting(SAVE_ROOT);
            Future<?> drain = workers.submit(() -> tracker.drain(SAVE_ROOT));

            assertThrows(TimeoutException.class, () -> drain.get(200, TimeUnit.MILLISECONDS),
                    "Shutdown must not pass the audit barrier while its callback is still running");
            releaseAudit.countDown();
            completion.get(5, TimeUnit.SECONDS);
            drain.get(5, TimeUnit.SECONDS);
            tracker.endSession(SAVE_ROOT);
            assertTrue(audited.get());
        } finally {
            releaseAudit.countDown();
            workers.shutdownNow();
        }
    }
}
