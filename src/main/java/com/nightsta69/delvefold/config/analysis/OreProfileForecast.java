package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.model.TerrainMode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, bounded, server-authoritative administrative forecast for one ore profile page. This contract
 * intentionally contains no world seed, coordinates, paths, or mutation data.
 *
 * <p>All collection components are immutable snapshots. Rule entries remain in profile order, height samples remain in
 * strictly ascending block-Y order, and all attempt/work metrics are finite, non-negative values measured per eligible
 * chunk. A forecast can omit bounded diagnostics while still reporting aggregate counters; {@code truncated} identifies
 * any such omission.
 *
 * @param formatVersion forecast wire-format version
 * @param profileId bounded profile identifier
 * @param profileRevision non-negative revision of the analyzed profile
 * @param activeTerrain active initialized terrain, or {@code null} for an uninitialized mining world
 * @param terrainTotals configured and effective workload totals in stable terrain order
 * @param activeTerrainHeightOverlay ascending expected workload samples for the active terrain
 * @param totalRuleCount bounded number of rules represented by paging metadata
 * @param page zero-based page index
 * @param pageSize maximum rules carried by each page
 * @param pageCount total pages, with one empty page retained for an empty profile
 * @param rules complete ordered rule slice for {@code page}
 * @param references bounded aggregate registry-reference summary
 * @param truncated whether rules, counters, targets, or diagnostic details were omitted to satisfy a bound
 */
public record OreProfileForecast(
        int formatVersion,
        String profileId,
        long profileRevision,
        @Nullable TerrainMode activeTerrain,
        List<TerrainTotals> terrainTotals,
        List<HeightSample> activeTerrainHeightOverlay,
        int totalRuleCount,
        int page,
        int pageSize,
        int pageCount,
        List<RuleForecast> rules,
        ReferenceSummary references,
        boolean truncated) {
    /** Current version of the bounded forecast data contract and its stream codec. */
    public static final int CURRENT_FORMAT_VERSION = 1;
    /** Default number of rule forecasts requested for one page. */
    public static final int DEFAULT_RULES_PER_PAGE = 12;
    /** Maximum rule forecasts allowed in one page. */
    public static final int MAX_RULES_PER_PAGE = 16;
    /** Maximum detailed reference issues retained for one visible rule. */
    public static final int MAX_RULE_ISSUES = 16;
    /** Maximum aggregated reference details retained in one forecast. */
    public static final int MAX_REFERENCE_DETAILS = 64;
    /** Maximum number of inclusive world-height samples carried by an active-terrain overlay. */
    public static final int MAX_HEIGHT_SAMPLES = 385;
    /** Maximum Unicode code points allowed in any forecast identifier. */
    public static final int MAX_IDENTIFIER_LENGTH = 128;
    /** Maximum number of profile rules represented by forecast paging. */
    public static final int MAX_TOTAL_RULES = 512;
    /** Maximum target count or target index range represented for one rule. */
    public static final int MAX_TARGETS_PER_RULE = 16;
    /** Saturation ceiling for detailed per-rule counters. */
    public static final int MAX_RULE_COUNTER = 4096;
    /** Saturation ceiling for whole-profile summary counters. */
    public static final int MAX_SUMMARY_COUNTER = 1_000_000;
    /** Lowest supported absolute block Y in an overlay. */
    public static final int MIN_HEIGHT = -64;
    /** Highest supported absolute block Y in an overlay. */
    public static final int MAX_HEIGHT = 320;
    /** Maximum conservative encoded-size estimate allowed for one forecast payload, in bytes. */
    public static final int MAX_ESTIMATED_NETWORK_BYTES = 24 * 1024;

    /**
     * Validates paging, ordering, uniqueness, finite metrics, immutable collections, and the network-size budget.
     *
     * @param formatVersion forecast wire-format version
     * @param profileId bounded profile identifier
     * @param profileRevision non-negative profile revision
     * @param activeTerrain active initialized terrain, or {@code null}
     * @param terrainTotals configured/effective totals for unique terrain modes
     * @param activeTerrainHeightOverlay strictly ascending active-terrain samples
     * @param totalRuleCount bounded number of represented profile rules
     * @param page zero-based page index
     * @param pageSize rules allocated to each page
     * @param pageCount total number of pages
     * @param rules complete rule slice for the selected page
     * @param references aggregate reference summary
     * @param truncated whether bounded content was omitted
     */
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
                || (activeTerrain != null
                        && terrainTotals.stream()
                                .noneMatch(totals -> totals.active() && totals.terrain() == activeTerrain))) {
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
        Objects.requireNonNull(references, "references");
        if (estimatedNetworkBytes(profileId, terrainTotals, activeTerrainHeightOverlay, rules, references)
                > MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException("Ore forecast exceeds its network-size budget");
        }
    }

    /**
     * Estimates the payload's encoded network footprint using fixed field overhead and UTF-8 identifier sizes.
     *
     * @return conservative estimated payload size in bytes
     */
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

    /**
     * Configured and runtime-effective workload totals for one terrain mode.
     *
     * @param terrain terrain represented by these totals
     * @param active whether this is the currently initialized terrain
     * @param configuredAttempts configured placement attempts per eligible chunk
     * @param configuredWorkUnits configured conservative block-placement work units per eligible chunk
     * @param effectiveAttempts attempts per eligible chunk after enabled, biome, validity, and output checks
     * @param effectiveWorkUnits work units per eligible chunk after the same runtime-effectiveness checks
     */
    public record TerrainTotals(
            TerrainMode terrain,
            boolean active,
            double configuredAttempts,
            double configuredWorkUnits,
            double effectiveAttempts,
            double effectiveWorkUnits) {
        /**
         * Validates the terrain and all finite, non-negative per-chunk workload metrics.
         *
         * @param terrain terrain represented by these totals
         * @param active whether this terrain is active
         * @param configuredAttempts configured placement attempts per eligible chunk
         * @param configuredWorkUnits configured work units per eligible chunk
         * @param effectiveAttempts effective placement attempts per eligible chunk
         * @param effectiveWorkUnits effective work units per eligible chunk
         */
        public TerrainTotals {
            Objects.requireNonNull(terrain, "terrain");
            metric(configuredAttempts, "configuredAttempts");
            metric(configuredWorkUnits, "configuredWorkUnits");
            metric(effectiveAttempts, "effectiveAttempts");
            metric(effectiveWorkUnits, "effectiveWorkUnits");
        }
    }

    /**
     * Expected active-terrain ore workload assigned to one absolute block height.
     *
     * @param y absolute block Y in the supported world range
     * @param expectedAttempts expected placement attempts at this Y per eligible chunk
     * @param expectedWorkUnits expected block-placement work units at this Y per eligible chunk
     */
    public record HeightSample(int y, double expectedAttempts, double expectedWorkUnits) {
        /**
         * Validates the block height and finite, non-negative expected workload values.
         *
         * @param y absolute block Y
         * @param expectedAttempts expected placement attempts at this Y per eligible chunk
         * @param expectedWorkUnits expected work units at this Y per eligible chunk
         */
        public HeightSample {
            if (y < MIN_HEIGHT || y > MAX_HEIGHT) {
                throw new IllegalArgumentException("Forecast height is outside the supported world range");
            }
            metric(expectedAttempts, "expectedAttempts");
            metric(expectedWorkUnits, "expectedWorkUnits");
        }
    }

    /**
     * Bounded configured/effective forecast for one rule in its original profile position.
     *
     * @param ruleIndex zero-based position in the profile
     * @param ruleId bounded rule identifier
     * @param enabled configured enabled state
     * @param required whether missing required outputs invalidate profile generation
     * @param status first applicable runtime-effectiveness classification
     * @param configuredAttempts configured placement attempts per eligible active-terrain chunk
     * @param configuredWorkUnits configured work units per eligible active-terrain chunk
     * @param effectiveAttempts attempts remaining after runtime-effectiveness checks
     * @param effectiveWorkUnits work units remaining after runtime-effectiveness checks
     * @param targetCount bounded number of configured targets
     * @param effectiveOutputCount bounded number of resolved effective output block states
     * @param missingReferenceCount bounded number of missing block or tag findings
     * @param shadowedOutputCount bounded number of outputs shadowed by target precedence
     * @param issues ordered, bounded detailed findings for this rule
     * @param truncated whether detailed rule data or counters were omitted or saturated
     */
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
        /**
         * Validates identifiers, counters, workload metrics, and the immutable issue-detail bound.
         *
         * @param ruleIndex zero-based profile position
         * @param ruleId bounded rule identifier
         * @param enabled configured enabled state
         * @param required configured required-output state
         * @param status runtime-effectiveness classification
         * @param configuredAttempts configured attempts per eligible chunk
         * @param configuredWorkUnits configured work units per eligible chunk
         * @param effectiveAttempts effective attempts per eligible chunk
         * @param effectiveWorkUnits effective work units per eligible chunk
         * @param targetCount bounded configured-target count
         * @param effectiveOutputCount bounded effective-output count
         * @param missingReferenceCount bounded missing-reference count
         * @param shadowedOutputCount bounded shadowed-output count
         * @param issues ordered detailed findings
         * @param truncated whether bounded rule information was omitted
         */
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

    /**
     * Whole-profile counts and first-occurrence details for registry and target-precedence findings.
     *
     * @param missingBlocks distinct missing exact block IDs
     * @param missingOutputTags distinct missing output block tags
     * @param missingHostTags distinct missing replacement/host tags
     * @param invalidStates invalid state-property or state-value findings
     * @param shadowedOutputs outputs made ineffective by earlier target precedence
     * @param totalIssues total findings before detail-list bounding, subject to the summary saturation ceiling
     * @param details ordered aggregate details keyed by first occurrence of kind and reference ID
     * @param truncated whether upstream findings or aggregate details were omitted or counters saturated
     */
    public record ReferenceSummary(
            int missingBlocks,
            int missingOutputTags,
            int missingHostTags,
            int invalidStates,
            int shadowedOutputs,
            int totalIssues,
            List<ReferenceIssue> details,
            boolean truncated) {
        /**
         * Validates bounded counters and defensively copies the ordered detail list.
         *
         * @param missingBlocks distinct missing exact block IDs
         * @param missingOutputTags distinct missing output tags
         * @param missingHostTags distinct missing host tags
         * @param invalidStates invalid state findings
         * @param shadowedOutputs shadowed output count
         * @param totalIssues bounded total finding count
         * @param details ordered aggregate details
         * @param truncated whether information was omitted or saturated
         */
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

    /**
     * One bounded registry-reference or target-precedence diagnostic.
     *
     * @param kind finding category
     * @param severity warning or configuration-invalidating error
     * @param ruleIndex zero-based profile rule position
     * @param targetIndex zero-based target position, or {@code -1} for a rule-level issue
     * @param ruleId bounded owning rule identifier
     * @param sourceId bounded exact block or block-tag source identifier
     * @param referenceId bounded missing, invalid, or shadowed reference identifier
     * @param affectedOutputs bounded number of resolved outputs affected by this finding
     */
    public record ReferenceIssue(
            IssueKind kind,
            IssueSeverity severity,
            int ruleIndex,
            int targetIndex,
            String ruleId,
            String sourceId,
            String referenceId,
            int affectedOutputs) {
        /**
         * Validates location indices, identifiers, severity, and the affected-output counter.
         *
         * @param kind finding category
         * @param severity warning or error severity
         * @param ruleIndex zero-based profile rule position
         * @param targetIndex zero-based target position or {@code -1}
         * @param ruleId owning rule identifier
         * @param sourceId source block or tag identifier
         * @param referenceId affected registry reference
         * @param affectedOutputs bounded affected-output count
         */
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

    /** Runtime-effectiveness state assigned using the builder's documented first-match precedence. */
    public enum RuleStatus {
        /** The enabled, valid rule can place at least one effective output in the active terrain. */
        EFFECTIVE,
        /** The rule is administratively disabled. */
        DISABLED,
        /** No mining terrain has been initialized, so effective runtime work is unavailable. */
        UNINITIALIZED,
        /** The rule excludes the active terrain mode. */
        TERRAIN_MISMATCH,
        /** The rule's biome filter cannot match the active terrain. */
        BIOME_MISMATCH,
        /** The rule is otherwise effective but has no positive runtime placement attempts. */
        NO_ATTEMPTS,
        /** Registry resolution produced no output block states that can be placed. */
        NO_EFFECTIVE_OUTPUTS,
        /** The rule structure or an error-severity target reference is invalid. */
        INVALID
    }

    /** Categories emitted while resolving host tags, outputs, state constraints, and target precedence. */
    public enum IssueKind {
        /** A replacement/host tag identifier is syntactically invalid. */
        INVALID_HOST_TAG,
        /** A syntactically valid replacement/host tag is absent from the registry snapshot. */
        MISSING_HOST_TAG,
        /** A target weight falls outside the supported inclusive range. */
        INVALID_WEIGHT,
        /** An exact output block ID is absent from the registry snapshot. */
        MISSING_BLOCK,
        /** An output block tag is absent or has no installed members. */
        MISSING_OUTPUT_TAG,
        /** A requested block-state property does not exist on an output block. */
        INVALID_STATE_PROPERTY,
        /** A requested value is invalid for an existing block-state property. */
        INVALID_STATE_VALUE,
        /** One or more resolved outputs are superseded by an earlier target. */
        SHADOWED_OUTPUT,
        /** An entire target is ineffective because all of its outputs were already claimed. */
        SHADOWED_TARGET
    }

    /** Severity of a forecast reference finding. */
    public enum IssueSeverity {
        /** Informational effectiveness problem that does not structurally invalidate the rule. */
        WARNING,
        /** Problem that makes the rule invalid for effective-work forecasting. */
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
