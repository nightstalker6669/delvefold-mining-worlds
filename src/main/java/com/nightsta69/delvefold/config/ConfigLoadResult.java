package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable result of loading or read-only validating the canonical configuration files.
 *
 * @param snapshot authoritative candidate or fallback snapshot safe for publication
 * @param usedFallback whether rejected disk state caused last-known-good or built-in defaults to be returned
 * @param issues immutable ordered diagnostics from parsing, schema, validation, consistency, and fallback selection
 */
public record ConfigLoadResult(ConfigSnapshot snapshot, boolean usedFallback, List<ConfigIssue> issues) {
    /**
     * Creates a result and takes an immutable snapshot of its diagnostics.
     *
     * @param snapshot configuration snapshot safe for use
     * @param usedFallback whether the returned snapshot did not come from the current accepted disk candidate
     * @param issues ordered diagnostics; malformed deserialization input with {@code null} becomes an empty list
     */
    public ConfigLoadResult(ConfigSnapshot snapshot, boolean usedFallback, @Nullable List<ConfigIssue> issues) {
        this.snapshot = snapshot;
        this.usedFallback = usedFallback;
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
