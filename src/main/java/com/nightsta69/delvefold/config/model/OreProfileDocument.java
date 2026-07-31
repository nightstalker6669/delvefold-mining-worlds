package com.nightsta69.delvefold.config.model;

import java.util.List;

public record OreProfileDocument(int schemaVersion, long revision, String profile, List<OreRule> rules) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public OreProfileDocument {
        profile = profile == null || profile.isBlank() ? "custom" : profile.trim();
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public OreProfileDocument nextRevision(List<OreRule> replacementRules, String replacementProfile) {
        return new OreProfileDocument(CURRENT_SCHEMA_VERSION, revision + 1, replacementProfile, replacementRules);
    }
}
