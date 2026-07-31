package com.nightsta69.delvefold.config.validation;

public record ConfigIssue(IssueSeverity severity, String code, String path, String message) {
    public static ConfigIssue error(String code, String path, String message) {
        return new ConfigIssue(IssueSeverity.ERROR, code, path, message);
    }

    public static ConfigIssue warning(String code, String path, String message) {
        return new ConfigIssue(IssueSeverity.WARNING, code, path, message);
    }
}
