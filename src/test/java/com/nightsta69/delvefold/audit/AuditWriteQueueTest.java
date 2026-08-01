package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditWriteQueueTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path temporary;

    @Test
    void closeDrainsAcceptedMutationsInOrderAndDurablyFlushesThem() throws Exception {
        RotatingAuditLog log = new RotatingAuditLog(temporary.resolve("ordered"), FIXED_CLOCK);
        List<String> failures = new CopyOnWriteArrayList<>();
        AuditWriteQueue queue = new AuditWriteQueue(log, (reason, failure) -> failures.add(reason));

        for (int revision = 0; revision < 64; revision++) {
            assertTrue(queue.offer(mutation(revision)));
        }
        queue.close();

        assertTrue(failures.isEmpty());
        List<Integer> revisions = Files.readAllLines(log.activePath()).stream()
                .map(JsonParser::parseString)
                .map(json -> json.getAsJsonObject().get("new_revision").getAsInt())
                .toList();
        assertEquals(java.util.stream.IntStream.range(0, 64).boxed().toList(), revisions);
        assertFalse(queue.accepting());
        assertFalse(queue.offer(mutation(65)), "A closed lifecycle must reject rather than lose writes silently");
        assertEquals(List.of("writer_unavailable"), failures);
    }

    @Test
    void boundedQueueRejectsExcessWithoutBlockingCallerAndKeepsAcceptedOrder() throws Exception {
        BlockingSink sink = new BlockingSink();
        List<String> failures = new CopyOnWriteArrayList<>();
        AuditWriteQueue queue =
                new AuditWriteQueue(sink, 2, daemonFactory("bounded-audit"), (reason, failure) -> failures.add(reason));

        assertTrue(queue.offer(mutation(0)));
        assertTrue(sink.started.await(2, TimeUnit.SECONDS));
        assertTrue(queue.offer(mutation(1)));
        assertTrue(queue.offer(mutation(2)));
        assertFalse(queue.offer(mutation(3)), "The caller must not wait when the bounded queue is full");
        assertEquals(List.of("queue_full"), failures);

        sink.release.countDown();
        queue.close();

        assertEquals(List.of(0L, 1L, 2L), sink.revisions);
        assertEquals(1, sink.flushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(sink.workerNames.stream().allMatch("bounded-audit"::equals));
    }

    private static AuditMutation mutation(long revision) {
        return new AuditMutation(
                "console",
                AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.ObjectType.SETTINGS,
                "settings-" + revision,
                revision - 1L,
                revision);
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class BlockingSink implements AuditWriteQueue.Sink {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Long> revisions = new ArrayList<>();
        private final List<String> workerNames = new ArrayList<>();
        private final AtomicInteger flushes = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public void append(AuditMutation mutation) throws Exception {
            started.countDown();
            release.await();
            revisions.add(mutation.newRevision());
            workerNames.add(Thread.currentThread().getName());
        }

        @Override
        public void flush() {
            flushes.incrementAndGet();
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
