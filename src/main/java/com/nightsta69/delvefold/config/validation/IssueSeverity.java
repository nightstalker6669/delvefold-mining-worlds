package com.nightsta69.delvefold.config.validation;

/** Indicates whether a configuration issue permits publication. */
public enum IssueSeverity {
    /** Non-rejecting problem whose affected optional behavior may be skipped or ineffective. */
    WARNING,
    /** Rejecting problem that prevents the candidate configuration from being published. */
    ERROR
}
