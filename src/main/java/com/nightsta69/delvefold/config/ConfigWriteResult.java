package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.List;

public record ConfigWriteResult(boolean saved, ConfigSnapshot snapshot, List<ConfigIssue> issues) {
    public ConfigWriteResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
