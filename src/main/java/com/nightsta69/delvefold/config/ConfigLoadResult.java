package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.List;

public record ConfigLoadResult(ConfigSnapshot snapshot, boolean usedFallback, List<ConfigIssue> issues) {
    public ConfigLoadResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
