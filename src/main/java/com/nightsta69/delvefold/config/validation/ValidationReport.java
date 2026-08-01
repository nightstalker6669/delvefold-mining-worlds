package com.nightsta69.delvefold.config.validation;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable ordered snapshot of configuration diagnostics.
 *
 * @param issues defensively copied issues in deterministic validation order
 */
public record ValidationReport(List<ConfigIssue> issues) {
    /**
     * Creates a report and takes an immutable snapshot of its diagnostics.
     *
     * @param issues issues to retain; malformed deserialization input with {@code null} becomes an empty list
     */
    public ValidationReport(@Nullable List<ConfigIssue> issues) {
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * Reports whether the candidate may be published.
     *
     * @return {@code true} when no issue has {@link IssueSeverity#ERROR} severity
     */
    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    /**
     * Counts rejecting diagnostics.
     *
     * @return number of error-severity issues
     */
    public long errorCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == IssueSeverity.ERROR)
                .count();
    }

    /**
     * Counts non-rejecting diagnostics.
     *
     * @return number of warning-severity issues
     */
    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == IssueSeverity.WARNING)
                .count();
    }
}
