package com.nightsta69.delvefold.config.validation;

import java.util.List;

public record ValidationReport(List<ConfigIssue> issues) {
    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    public long errorCount() {
        return issues.stream().filter(issue -> issue.severity() == IssueSeverity.ERROR).count();
    }

    public long warningCount() {
        return issues.stream().filter(issue -> issue.severity() == IssueSeverity.WARNING).count();
    }
}
