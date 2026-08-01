package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable outcome of a configuration mutation attempt.
 *
 * @param saved whether validation, optimistic revision checks, persistence, and publication all succeeded
 * @param snapshot resulting snapshot on success, prior authoritative snapshot on many rejections, or {@code null} when
 *     the service has no current snapshot
 * @param issues immutable ordered rejection diagnostics and nonfatal warnings
 */
public record ConfigWriteResult(boolean saved, @Nullable ConfigSnapshot snapshot, List<ConfigIssue> issues) {
    /**
     * Creates a write result and takes an immutable snapshot of its diagnostics.
     *
     * @param saved whether the candidate was committed
     * @param snapshot resulting or prior authoritative snapshot, when available
     * @param issues ordered diagnostics; malformed deserialization input with {@code null} becomes an empty list
     */
    public ConfigWriteResult(boolean saved, @Nullable ConfigSnapshot snapshot, @Nullable List<ConfigIssue> issues) {
        this.saved = saved;
        this.snapshot = snapshot;
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
