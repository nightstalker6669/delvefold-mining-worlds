package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Pure acceptance policy for applying recreation-locked fields during a live configuration reload. */
final class LiveConfigReloadPolicy {
    private LiveConfigReloadPolicy() {}

    /**
     * Retains the active snapshot when an otherwise readable candidate changes lifecycle-owned settings.
     *
     * <p>Repository fallbacks are passed through unchanged so their original diagnostics and fallback selection remain
     * authoritative. A rejected live candidate appends ordered lifecycle errors and the established last-known-good
     * warning after any repository diagnostics.
     *
     * @param active currently published snapshot, or {@code null} before the first publication
     * @param result repository load or validation result
     * @return the original result when acceptable, otherwise a fallback result containing {@code active}
     */
    static ConfigLoadResult enforce(@Nullable ConfigSnapshot active, ConfigLoadResult result) {
        if (active == null || result.usedFallback()) {
            return result;
        }
        List<ConfigIssue> lifecycleIssues = LiveSettingsTransition.validate(
                active.settings(), result.snapshot().settings());
        if (lifecycleIssues.isEmpty()) {
            return result;
        }
        List<ConfigIssue> issues = new ArrayList<>(result.issues());
        issues.addAll(lifecycleIssues);
        issues.add(ConfigIssue.warning(
                "fallback.last_good",
                "$",
                "Lifecycle-owned settings changed on disk; continuing with the active snapshot"));
        return new ConfigLoadResult(active, true, issues);
    }
}
