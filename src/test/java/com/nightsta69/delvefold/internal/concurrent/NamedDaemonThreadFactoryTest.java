package com.nightsta69.delvefold.internal.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NamedDaemonThreadFactoryTest {

    @Test
    void namesBeginAtOneAndEachFactoryOwnsItsSequence() {
        NamedDaemonThreadFactory first = new NamedDaemonThreadFactory("first-");
        NamedDaemonThreadFactory second = new NamedDaemonThreadFactory("second-");

        assertEquals(
                "first-1", first.newThread(NamedDaemonThreadFactoryTest::noOp).getName());
        assertEquals(
                "first-2", first.newThread(NamedDaemonThreadFactoryTest::noOp).getName());
        assertEquals(
                "second-1", second.newThread(NamedDaemonThreadFactoryTest::noOp).getName());
        assertEquals(
                "first-3", first.newThread(NamedDaemonThreadFactoryTest::noOp).getName());
    }

    @Test
    void defaultFactoryCreatesDaemonWithoutInstallingAThreadHandler() {
        Thread thread = new NamedDaemonThreadFactory("default-").newThread(NamedDaemonThreadFactoryTest::noOp);

        assertTrue(thread.isDaemon());
        assertSame(
                thread.getThreadGroup(),
                thread.getUncaughtExceptionHandler(),
                "An unset per-thread handler delegates through the thread group and JVM default behavior");
    }

    @Test
    void explicitHandlerIsInstalledOnEveryThread() {
        Thread.UncaughtExceptionHandler handler = (ignored, failure) -> {};
        NamedDaemonThreadFactory factory = new NamedDaemonThreadFactory("handled-", handler);

        assertSame(
                handler, factory.newThread(NamedDaemonThreadFactoryTest::noOp).getUncaughtExceptionHandler());
        assertSame(
                handler, factory.newThread(NamedDaemonThreadFactoryTest::noOp).getUncaughtExceptionHandler());
    }

    @Test
    void concurrentCreationProducesOneUniqueContiguousSequence() throws InterruptedException {
        int callerCount = 32;
        NamedDaemonThreadFactory factory = new NamedDaemonThreadFactory("parallel-");
        Set<String> names = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> callers = java.util.stream.IntStream.range(0, callerCount)
                .mapToObj(index -> new Thread(
                        () -> {
                            ready.countDown();
                            try {
                                start.await();
                                names.add(factory.newThread(NamedDaemonThreadFactoryTest::noOp)
                                        .getName());
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "factory-caller-" + index))
                .toList();

        callers.forEach(Thread::start);
        boolean allReady = ready.await(5L, TimeUnit.SECONDS);
        start.countDown();
        assertTrue(allReady, "All callers should reach the creation barrier");
        for (Thread caller : callers) {
            caller.join(5_000L);
            assertFalse(caller.isAlive(), "Concurrent factory caller should complete");
        }

        Set<String> expected = new HashSet<>();
        for (int sequence = 1; sequence <= callerCount; sequence++) {
            expected.add("parallel-" + sequence);
        }
        assertEquals(expected, names);
    }

    private static void noOp() {}
}
