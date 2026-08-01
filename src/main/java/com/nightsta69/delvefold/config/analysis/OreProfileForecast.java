package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.model.TerrainMode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, bounded, server-authoritative administrative forecast for one ore profile page. This contract
 * intentionally contains no world seed, coordinates, paths, or mutation data.
 */
public record OreProfileForecast(
        int formatVersion,
        String profileId,
        long profileRevision,
        TerrainMode activeTerrain,
        List<TerrainTotals> terrainTotals,
        List<HeightSample> activeTerrainHeightOverlay,
        int totalRuleCount,
        int page,
        int pageSize,
        int pageCount,
        List<RuleForecast> rules,
        ReferenceSummary references,
        boolean truncated) {
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final int DEFAULT_RULES_PER_PAGE = 12;
    public static final int MAX_RULES_PER_PAGE = 16;
    public static final int MAX_RULE_ISSUES = 16;
    public static final int MAX_REFERENCE_DETAILS = 64;
    public static final int MAX_HEIGHT_SAMPLES = 385;
    public static final int MAX_IDENTIFIER_LENGTH = 128;
    public static final int MAX_TOTAL_RULES = 512;
    public static final int MAX_TARGETS_PER_RULE = 16;
    public static final int MAX_RULE_COUNTER = 4096;
    public static final int MAX_SUMMARY_COUNTER = 1_000_000;
    public static final int MIN_HEIGHT = -64;
    public static final int MAX_HEIGHT = 320;
    public static final int MAX_ESTIMATED_NETWORK_BYTES = 24 * 1024;

    public OreProfileForecast {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported ore forecast format: " + formatVersion);
        }
        profileId = identifier(profileId, "profileId");
        if (profileRevision < 0L) {
            throw new IllegalArgumentException("Profile revision cannot be negative");
        }
        terrainTotals = terrainTotals == null ? List.of() : List.copyOf(terrainTotals);
        if (terrainTotals.size() > TerrainMode.values().length) {
            throw new IllegalArgumentException("Too many terrain totals");
        }
        Set<TerrainMode> uniqueTerrains = new HashSet<>();
        for (TerrainTotals totals : terrainTotals) {
            if (!uniqueTerrains.add(totals.terrain())) {
                throw new IllegalArgumentException("Duplicate terrain total: " + totals.terrain());
            }
        }
        long activeTotals = terrainTotals.stream().filter(TerrainTotals::active).count();
        if ((activeTerrain == null ? activeTotals != 0L : activeTotals != 1L)
                || activeTerrain != null
                        && terrainTotals.stream()
                                .noneMatch(totals -> totals.active() && totals.terrain() == activeTerrain)) {
            throw new IllegalArgumentException("Terrain totals do not identify the active terrain");
        }
        activeTerrainHeightOverlay =
                activeTerrainHeightOverlay == null ? List.of() : List.copyOf(activeTerrainHeightOverlay);
        if (activeTerrainHeightOverlay.size() > MAX_HEIGHT_SAMPLES) {
            throw new IllegalArgumentException("Too many height samples");
        }
        int previousY = Integer.MIN_VALUE;
        for (HeightSample sample : activeTerrainHeightOverlay) {
            if (sample.y() <= previousY) {
                throw new IllegalArgumentException("Height samples must be strictly ordered");
            }
            previousY = sample.y();
        }
        if (activeTerrain == null && !activeTerrainHeightOverlay.isEmpty()) {
            throw new IllegalArgumentException("Uninitialized forecasts cannot contain a height overlay");
        }
        if (totalRuleCount < 0 || totalRuleCount > MAX_TOTAL_RULES) {
            throw new IllegalArgumentException("Invalid total rule count: " + totalRuleCount);
        }
        if (pageSize < 1 || pageSize > MAX_RULES_PER_PAGE) {
            throw new IllegalArgumentException("Invalid forecast page size: " + pageSize);
        }
        int expectedPages = Math.max(1, (totalRuleCount + pageSize - 1) / pageSize);
        if (pageCount != expectedPages || page < 0 || page >= pageCount) {
            throw new IllegalArgumentException("Invalid forecast paging metadata");
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (rules.size() > pageSize || rules.size() > MAX_RULES_PER_PAGE) {
            throw new IllegalArgumentException("Too many rules in forecast page");
        }
        int firstRule = page * pageSize;
        int lastRule = Math.min(totalRuleCount, firstRule + pageSize);
        if (rules.size() != lastRule - firstRule) {
            throw new IllegalArgumentException("Forecast page does not contain its complete bounded rule slice");
        }
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).ruleIndex() != firstRule + index) {
                throw new IllegalArgumentException("Forecast rules are not in profile order");
            }
        }
        references = Objects.requireNonNull(references, "references");
        if (estimatedNetworkBytes(profileId, terrainTotals, activeTerrainHeightOverlay, rules, references)
                > MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException("Ore forecast exceeds its network-size budget");
        }
    }

    public int estimatedNetworkBytes() {
        return estimatedNetworkBytes(profileId, terrainTotals, activeTerrainHeightOverlay, rules, references);
    }

    private static int estimatedNetworkBytes(
            String profileId,
            List<TerrainTotals> terrainTotals,
            List<HeightSample> activeTerrainHeightOverlay,
            List<RuleForecast> rules,
            ReferenceSummary references) {
        int bytes = 128 + networkStringBytes(profileId);
        bytes += terrainTotals.size() * 48;
        bytes += activeTerrainHeightOverlay.size() * 24;
        for (RuleForecast rule : rules) {
            bytes += rule.estimatedNetworkBytes();
        }
        return bytes + references.estimatedNetworkBytes();
    }

    public record TerrainTotals(
            TerrainMode terrain,
            boolean active,
            double configuredAttempts,
            double configuredWorkUnits,
            double effectiveAttempts,
            double effectiveWorkUnits) {
        public TerrainTotals {
            Objects.requireNonNull(terrain, "terrain");
            metric(configuredAttempts, "configuredAttempts");
            metric(configuredWorkUnits, "configuredWorkUnits");
            metric(effectiveAttempts, "effectiveAttempts");
            metric(effectiveWorkUnits, "effectiveWorkUnits");
        }
    }

    public record HeightSample(int y, double expectedAttempts, double expectedWorkUnits) {
        public HeightSample {
            if (y < MIN_HEIGHT || y > MAX_HEIGHT) {
                throw new IllegalArgumentException("Forecast height is outside the supported world range");
            }
            metric(expectedAttempts, "expectedAttempts");
            metric(expectedWorkUnits, "expectedWorkUnits");
        }
    }

    public record RuleForecast(
            int ruleIndex,
            String ruleId,
            boolean enabled,
            boolean required,
            RuleStatus status,
            double configuredAttempts,
            double configuredWorkUnits,
            double effectiveAttempts,
            double effectiveWorkUnits,
            int targetCount,
            int effectiveOutputCount,
            int missingReferenceCount,
            int shadowedOutputCount,
            List<ReferenceIssue> issues,
            boolean truncated) {
        public RuleForecast {
            if (ruleIndex < 0 || ruleIndex >= MAX_TOTAL_RULES) {
                throw new IllegalArgumentException("Invalid forecast rule index: " + ruleIndex);
            }
            ruleId = identifier(ruleId, "ruleId");
            Objects.requireNonNull(status, "status");
            metric(configuredAttempts, "configuredAttempts");
            metric(configuredWorkUnits, "configuredWorkUnits");
            metric(effectiveAttempts, "effectiveAttempts");
            metric(effectiveWorkUnits, "effectiveWorkUnits");
            count(targetCount, "targetCount");
            count(effectiveOutputCount, "effectiveOutputCount");
            count(missingReferenceCount, "missingReferenceCount");
            count(shadowedOutputCount, "shadowedOutputCount");
            if (targetCount > MAX_TARGETS_PER_RULE
                    || effectiveOutputCount > MAX_RULE_COUNTER
                    || missingReferenceCount > MAX_RULE_COUNTER
                    || shadowedOutputCount > MAX_RULE_COUNTER) {
                throw new IllegalArgumentException("Forecast rule counter exceeds its network bound");
            }
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (issues.size() > MAX_RULE_ISSUES) {
                throw new IllegalArgumentException("Too many issues for one forecast rule");
            }
        }

        int estimatedNetworkBytes() {
            int bytes = 96 + networkStringBytes(ruleId);
            for (ReferenceIssue issue : issues) {
                bytes += issue.estimatedNetworkBytes();
            }
            return bytes;
        }
    }

    public record ReferenceSummary(
            int missingBlocks,
            int missingOutputTags,
            int missingHostTags,
            int invalidStates,
            int shadowedOutputs,
            int totalIssues,
            List<ReferenceIssue> details,
            boolean truncated) {
        public ReferenceSummary {
            count(missingBlocks, "missingBlocks");
            count(missingOutputTags, "missingOutputTags");
            count(missingHostTags, "missingHostTags");
            count(invalidStates, "invalidStates");
            count(shadowedOutputs, "shadowedOutputs");
            count(totalIssues, "totalIssues");
            if (missingBlocks > MAX_SUMMARY_COUNTER
                    || missingOutputTags > MAX_SUMMARY_COUNTER
                    || missingHostTags > MAX_SUMMARY_COUNTER
                    || invalidStates > MAX_SUMMARY_COUNTER
                    || shadowedOutputs > MAX_SUMMARY_COUNTER
                    || totalIssues > MAX_SUMMARY_COUNTER) {
                throw new IllegalArgumentException("Forecast summary counter exceeds its network bound");
            }
            details = details == null ? List.of() : List.copyOf(details);
            if (details.size() > MAX_REFERENCE_DETAILS || totalIssues < details.size()) {
                throw new IllegalArgumentException("Invalid forecast reference details");
            }
        }

        int estimatedNetworkBytes() {
            int bytes = 64;
            for (ReferenceIssue issue : details) {
                bytes += issue.estimatedNetworkBytes();
            }
            return bytes;
        }
    }

    public record ReferenceIssue(
            IssueKind kind,
            IssueSeverity severity,
            int ruleIndex,
            int targetIndex,
            String ruleId,
            String sourceId,
            String referenceId,
            int affectedOutputs) {
        public ReferenceIssue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            if (ruleIndex < 0
                    || ruleIndex >= MAX_TOTAL_RULES
                    || targetIndex < -1
                    || targetIndex >= MAX_TARGETS_PER_RULE) {
                throw new IllegalArgumentException("Invalid forecast issue location");
            }
            ruleId = identifier(ruleId, "ruleId");
            sourceId = identifier(sourceId, "sourceId");
            referenceId = identifier(referenceId, "referenceId");
            count(affectedOutputs, "affectedOutputs");
            if (affectedOutputs > MAX_RULE_COUNTER) {
                throw new IllegalArgumentException("Forecast issue counter exceeds its network bound");
            }
        }

        int estimatedNetworkBytes() {
            return 40 + networkStringBytes(ruleId) + networkStringBytes(sourceId) + networkStringBytes(referenceId);
        }
    }

    public enum RuleStatus {
        EFFECTIVE,
        DISABLED,
        UNINITIALIZED,
        TERRAIN_MISMATCH,
        BIOME_MISMATCH,
        NO_ATTEMPTS,
        NO_EFFECTIVE_OUTPUTS,
        INVALID
    }

    public enum IssueKind {
        INVALID_HOST_TAG,
        MISSING_HOST_TAG,
        INVALID_WEIGHT,
        MISSING_BLOCK,
        MISSING_OUTPUT_TAG,
        INVALID_STATE_PROPERTY,
        INVALID_STATE_VALUE,
        SHADOWED_OUTPUT,
        SHADOWED_TARGET
    }

    public enum IssueSeverity {
        WARNING,
        ERROR
    }

    private static String identifier(String value, String label) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_IDENTIFIER_LENGTH + " characters");
        }
        return safe;
    }

    private static void metric(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void count(int value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }

    private static int networkStringBytes(String value) {
        return 5 + value.getBytes(StandardCharsets.UTF_8).length;
    }
}
