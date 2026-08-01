package com.nightsta69.delvefold.config.validation;

import org.jspecify.annotations.Nullable;

/**
 * Machine-stable configuration diagnostic suitable for validation reports and localized display projection.
 *
 * @param severity warning or rejecting error severity
 * @param code stable diagnostic code used for localization and automated inspection
 * @param path JSONPath-like location of the affected setting
 * @param message optional technical detail; callers must not treat it as a stable or localized identifier
 */
public record ConfigIssue(
        IssueSeverity severity,
        String code,
        String path,
        @Nullable String message) {
    /**
     * Creates a rejecting configuration diagnostic.
     *
     * @param code stable diagnostic code
     * @param path JSONPath-like configuration location
     * @param message optional technical detail
     * @return error-severity issue
     */
    public static ConfigIssue error(String code, String path, @Nullable String message) {
        return new ConfigIssue(IssueSeverity.ERROR, code, path, message);
    }

    /**
     * Creates a non-rejecting configuration diagnostic.
     *
     * @param code stable diagnostic code
     * @param path JSONPath-like configuration location
     * @param message technical detail
     * @return warning-severity issue
     */
    public static ConfigIssue warning(String code, String path, String message) {
        return new ConfigIssue(IssueSeverity.WARNING, code, path, message);
    }
}
