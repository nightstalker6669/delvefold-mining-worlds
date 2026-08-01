package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DoctorReportCacheTest {
    @Test
    void invalidatePreventsSameSaveReopenFromSeeingThePriorSession() {
        DoctorReportCache cache = new DoctorReportCache(2);
        Path save = Path.of("build/test-save");
        DoctorReport oldSession = new DoctorReportBuilder(1L).build();

        cache.put(save, oldSession, 10L);
        assertSame(oldSession, Objects.requireNonNull(cache.get(save)).report());
        cache.invalidate(save.toAbsolutePath().normalize());

        assertNull(cache.get(save));
        assertEquals(0, cache.size());
    }

    @Test
    void clearDropsEverySaveAndCapacityUsesLeastRecentlyAccessedEviction() {
        DoctorReportCache cache = new DoctorReportCache(2);
        Path first = Path.of("build/save-a");
        Path second = Path.of("build/save-b");
        Path third = Path.of("build/save-c");
        DoctorReport report = new DoctorReportBuilder(1L).build();

        cache.put(first, report, 1L);
        cache.put(second, report, 2L);
        cache.get(first);
        cache.put(third, report, 3L);
        assertNull(cache.get(second));
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get(first));
        assertNull(cache.get(third));
    }
}
