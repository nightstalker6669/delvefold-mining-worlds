package com.nightsta69.delvefold.diagnostics;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Small access-ordered cache whose keys are normalized save roots. */
final class DoctorReportCache {
    private final int maximumSaves;
    private final LinkedHashMap<Path, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

    DoctorReportCache(int maximumSaves) {
        if (maximumSaves < 1) {
            throw new IllegalArgumentException("maximumSaves must be positive");
        }
        this.maximumSaves = maximumSaves;
    }

    synchronized Entry get(Path saveRoot) {
        return entries.get(key(saveRoot));
    }

    synchronized void put(Path saveRoot, DoctorReport report, long cachedAtEpochMillis) {
        if (cachedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("cachedAtEpochMillis must not be negative");
        }
        entries.put(key(saveRoot), new Entry(Objects.requireNonNull(report, "report"), cachedAtEpochMillis));
        while (entries.size() > maximumSaves) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    synchronized void invalidate(Path saveRoot) {
        entries.remove(key(saveRoot));
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }

    private static Path key(Path saveRoot) {
        return Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
    }

    record Entry(DoctorReport report, long cachedAtEpochMillis) {}
}
