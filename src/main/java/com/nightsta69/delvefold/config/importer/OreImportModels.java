package com.nightsta69.delvefold.config.importer;

import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, explicitly bounded values shared by discovery, preview, and later network adapters. */
public final class OreImportModels {
    public static final int MAX_GROUPS = 256;
    public static final int MAX_SELECTED_GROUPS = 128;
    public static final int MAX_CANDIDATES_PER_GROUP = 16;
    public static final int MAX_SOURCE_TAGS_PER_CANDIDATE = 8;
    public static final int MAX_DIFF_ENTRIES = MAX_SELECTED_GROUPS;
    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_MESSAGE_LENGTH = 512;
    public static final int MAX_SCANNED_BLOCKS = 1_000_000;

    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern MATERIAL = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern PROFILE_ID = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");

    private OreImportModels() {}

    public record DiscoveryOptions(boolean includeVanilla) {
        public static final DiscoveryOptions MODDED_ONLY = new DiscoveryOptions(false);
        public static final DiscoveryOptions INCLUDING_VANILLA = new DiscoveryOptions(true);
    }

    public record DiscoveryResult(List<Group> groups, boolean truncated, int scannedBlocks) {
        public DiscoveryResult {
            groups = bounded(groups, MAX_GROUPS, "ore import groups").stream()
                    .sorted(Comparator.comparing(Group::id))
                    .toList();
            if (scannedBlocks < 0 || scannedBlocks > MAX_SCANNED_BLOCKS) {
                throw new IllegalArgumentException("Invalid scanned block count: " + scannedBlocks);
            }
        }
    }

    public record Group(
            String id,
            String namespace,
            String material,
            Evidence evidence,
            List<Candidate> candidates,
            boolean reviewRequired) {
        public Group {
            id = resourceId(id, "group id");
            namespace = matching(namespace, NAMESPACE, "namespace");
            material = matching(material, MATERIAL, "material");
            evidence = Objects.requireNonNull(evidence, "evidence");
            candidates = bounded(candidates, MAX_CANDIDATES_PER_GROUP, "ore import candidates").stream()
                    .sorted(Comparator.comparing(Candidate::blockId))
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("An ore import group needs at least one candidate");
            }
            reviewRequired = reviewRequired
                    || candidates.stream().anyMatch(candidate -> candidate.hostKind() == HostKind.REVIEW_REQUIRED);
        }
    }

    public record Candidate(
            String blockId, String replaceTag, HostKind hostKind, Evidence evidence, List<String> sourceTags) {
        public Candidate {
            blockId = resourceId(blockId, "candidate block id");
            hostKind = Objects.requireNonNull(hostKind, "hostKind");
            evidence = Objects.requireNonNull(evidence, "evidence");
            String normalizedReplaceTag = replaceTag == null ? "" : stripHash(replaceTag.trim());
            if (normalizedReplaceTag.isEmpty() && hostKind != HostKind.REVIEW_REQUIRED) {
                normalizedReplaceTag = hostKind.replaceTag();
            }
            if (!normalizedReplaceTag.isEmpty()) {
                normalizedReplaceTag = resourceId(normalizedReplaceTag, "replacement tag");
            }
            if (hostKind == HostKind.REVIEW_REQUIRED && !normalizedReplaceTag.isEmpty()) {
                throw new IllegalArgumentException("Review-required candidates cannot guess a replacement tag");
            }
            replaceTag = normalizedReplaceTag;
            sourceTags = bounded(sourceTags, MAX_SOURCE_TAGS_PER_CANDIDATE, "candidate source tags").stream()
                    .map(tag -> resourceId(stripHash(tag), "candidate source tag"))
                    .distinct()
                    .sorted()
                    .toList();
        }

        public boolean reviewRequired() {
            return hostKind == HostKind.REVIEW_REQUIRED;
        }
    }

    public enum Evidence {
        ORE_LIKE_NAME(0),
        COMMON_ORES_TAG(1),
        CONVENTIONAL_TAG(2);

        private final int strength;

        Evidence(int strength) {
            this.strength = strength;
        }

        public int strength() {
            return strength;
        }

        public static Evidence strongest(Evidence left, Evidence right) {
            return left.strength >= right.strength ? left : right;
        }
    }

    public enum HostKind {
        STONE("minecraft:stone_ore_replaceables"),
        DEEPSLATE("minecraft:deepslate_ore_replaceables"),
        REVIEW_REQUIRED("");

        private final String replaceTag;

        HostKind(String replaceTag) {
            this.replaceTag = replaceTag;
        }

        public String replaceTag() {
            return replaceTag;
        }
    }

    public record DiffEntry(
            String groupId,
            DiffStatus status,
            String ruleId,
            List<String> addedBlocks,
            List<String> skippedBlocks,
            String message) {
        public DiffEntry {
            groupId = resourceId(groupId, "diff group id");
            status = Objects.requireNonNull(status, "status");
            ruleId = optionalSimpleId(ruleId, "diff rule id");
            addedBlocks = blockIds(addedBlocks, "added blocks");
            skippedBlocks = blockIds(skippedBlocks, "skipped blocks");
            message = message == null ? "" : message.trim();
            if (message.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException("Diff message exceeds " + MAX_MESSAGE_LENGTH + " characters");
            }
        }

        private static List<String> blockIds(List<String> values, String label) {
            return bounded(values, MAX_CANDIDATES_PER_GROUP, label).stream()
                    .map(value -> resourceId(value, label))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    public enum DiffStatus {
        ADDED,
        PARTIALLY_ADDED,
        SKIPPED_COVERED,
        SKIPPED_REVIEW_REQUIRED
    }

    public record TerrainWorkload(double attemptsPerChunk, double workUnits) {
        public static final TerrainWorkload ZERO = new TerrainWorkload(0.0D, 0.0D);

        public TerrainWorkload {
            if (!Double.isFinite(attemptsPerChunk)
                    || attemptsPerChunk < 0.0D
                    || !Double.isFinite(workUnits)
                    || workUnits < 0.0D) {
                throw new IllegalArgumentException("Ore workload values must be finite and non-negative");
            }
        }
    }

    public record Workload(Map<TerrainMode, TerrainWorkload> byTerrain) {
        public Workload {
            EnumMap<TerrainMode, TerrainWorkload> normalized = new EnumMap<>(TerrainMode.class);
            for (TerrainMode terrain : TerrainMode.values()) {
                normalized.put(
                        terrain,
                        byTerrain == null
                                ? TerrainWorkload.ZERO
                                : Objects.requireNonNullElse(byTerrain.get(terrain), TerrainWorkload.ZERO));
            }
            byTerrain = Collections.unmodifiableMap(normalized);
        }

        public TerrainWorkload forTerrain(TerrainMode terrain) {
            return byTerrain.get(Objects.requireNonNull(terrain, "terrain"));
        }
    }

    public record Plan(
            String baseProfileId,
            OreProfileDocument proposedProfile,
            List<DiffEntry> diff,
            Workload beforeWorkload,
            Workload afterWorkload,
            ValidationReport validation) {
        public Plan {
            baseProfileId = matching(baseProfileId, PROFILE_ID, "base profile id");
            proposedProfile = Objects.requireNonNull(proposedProfile, "proposedProfile");
            diff = bounded(diff, MAX_DIFF_ENTRIES, "ore import diff entries");
            beforeWorkload = Objects.requireNonNull(beforeWorkload, "beforeWorkload");
            afterWorkload = Objects.requireNonNull(afterWorkload, "afterWorkload");
            validation = Objects.requireNonNull(validation, "validation");
        }

        public boolean valid() {
            return validation.valid();
        }

        public long addedRuleCount() {
            return diff.stream()
                    .filter(entry -> entry.status() == DiffStatus.ADDED || entry.status() == DiffStatus.PARTIALLY_ADDED)
                    .count();
        }
    }

    private static String resourceId(String value, String label) {
        return matching(value, RESOURCE_ID, label);
    }

    private static String matching(String value, Pattern pattern, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_ID_LENGTH
                || !pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + normalized);
        }
        return normalized;
    }

    private static String optionalSimpleId(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() > MAX_ID_LENGTH
                || !NAMESPACE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + normalized);
        }
        return normalized;
    }

    private static String stripHash(String value) {
        return value != null && value.startsWith("#") ? value.substring(1) : value;
    }

    private static <T> List<T> bounded(List<T> values, int maximum, String label) {
        List<T> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > maximum) {
            throw new IllegalArgumentException(label + " exceed the limit of " + maximum);
        }
        return copy;
    }
}
