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
import org.jspecify.annotations.Nullable;

/** Immutable, explicitly bounded values shared by discovery, preview, and later network adapters. */
public final class OreImportModels {
    /** Maximum ore-family groups retained by one discovery result. */
    public static final int MAX_GROUPS = 256;
    /** Maximum distinct ore-family groups accepted by one preview request. */
    public static final int MAX_SELECTED_GROUPS = 128;
    /** Maximum exact targets enabled in one generated rule and retained in one preview-detail list. */
    public static final int MAX_ENABLED_CANDIDATES_PER_RULE = 16;
    /** Maximum installed block variants retained for one logical material family during discovery. */
    public static final int MAX_DISCOVERED_CANDIDATES_PER_GROUP = 256;
    /** Maximum evidence tag IDs retained for one discovered candidate. */
    public static final int MAX_SOURCE_TAGS_PER_CANDIDATE = 8;
    /** Maximum diff rows retained in one import plan. */
    public static final int MAX_DIFF_ENTRIES = MAX_SELECTED_GROUPS;
    /** Maximum Java string length accepted for a resource, group, material, profile, or rule identifier. */
    public static final int MAX_ID_LENGTH = 128;
    /** Maximum Java string length accepted for one localized diff message. */
    public static final int MAX_MESSAGE_LENGTH = 512;
    /** Maximum installed block entries inspected by one bounded discovery pass. */
    public static final int MAX_SCANNED_BLOCKS = 1_000_000;

    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern MATERIAL = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern PROFILE_ID = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");

    private OreImportModels() {}

    /**
     * Namespace scope for one registry discovery pass.
     *
     * @param includeVanilla whether candidates in the {@code minecraft} namespace may be returned
     */
    public record DiscoveryOptions(boolean includeVanilla) {
        /** Standard modded-ore scan that excludes the vanilla namespace. */
        public static final DiscoveryOptions MODDED_ONLY = new DiscoveryOptions(false);
        /** Broad scan that includes both vanilla and modded namespaces. */
        public static final DiscoveryOptions INCLUDING_VANILLA = new DiscoveryOptions(true);
    }

    /**
     * Bounded, deterministically ordered result of scanning installed block registrations.
     *
     * @param groups ore families sorted by group ID
     * @param truncated whether entries were omitted because of bounds or unsupported identifiers
     * @param scannedBlocks number of source block entries inspected, capped at {@link #MAX_SCANNED_BLOCKS}
     */
    public record DiscoveryResult(List<Group> groups, boolean truncated, int scannedBlocks) {
        /**
         * Defensively copies, bounds, and sorts groups while validating the scanned-block count.
         *
         * @param groups discovered ore families, or {@code null} for none
         * @param truncated whether discovery omitted any source or result data
         * @param scannedBlocks bounded number of inspected block entries
         */
        public DiscoveryResult(@Nullable List<Group> groups, boolean truncated, int scannedBlocks) {
            groups = bounded(groups, MAX_GROUPS, "ore import groups").stream()
                    .sorted(Comparator.comparing(Group::id))
                    .toList();
            if (scannedBlocks < 0 || scannedBlocks > MAX_SCANNED_BLOCKS) {
                throw new IllegalArgumentException("Invalid scanned block count: " + scannedBlocks);
            }
            this.groups = groups;
            this.truncated = truncated;
            this.scannedBlocks = scannedBlocks;
        }
    }

    /**
     * Probable variants of one logical ore material across all installed providers.
     *
     * @param id resource-form, provider-independent family ID
     * @param namespace namespace of the family ID, not an ore provider namespace
     * @param material normalized material path
     * @param evidence strongest discovery evidence among retained candidates
     * @param candidates candidate block variants sorted by registry ID
     * @param reviewRequired whether the family contains an uncertain identity or ambiguous host that should be reviewed
     */
    public record Group(
            String id,
            String namespace,
            String material,
            Evidence evidence,
            List<Candidate> candidates,
            boolean reviewRequired) {
        /**
         * Validates identifiers, copies and sorts candidates, and propagates candidate review requirements.
         *
         * @param id group resource ID
         * @param namespace namespace of the provider-independent family ID
         * @param material normalized material path
         * @param evidence strongest discovery evidence
         * @param candidates one or more bounded candidate variants
         * @param reviewRequired explicit upstream review requirement
         */
        public Group {
            id = resourceId(id, "group id");
            namespace = matching(namespace, NAMESPACE, "namespace");
            material = matching(material, MATERIAL, "material");
            Objects.requireNonNull(evidence, "evidence");
            candidates = bounded(candidates, MAX_DISCOVERED_CANDIDATES_PER_GROUP, "ore import candidates").stream()
                    .sorted(Comparator.comparing(Candidate::blockId))
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("An ore import group needs at least one candidate");
            }
            reviewRequired = reviewRequired
                    || candidates.stream().anyMatch(Candidate::reviewRequired)
                    || candidates.stream().anyMatch(Candidate::identifiedByFallback);
        }

        /**
         * Returns the provider namespaces represented by this material family.
         *
         * @return distinct provider namespaces in lexical order
         */
        public List<String> providerNamespaces() {
            return candidates.stream()
                    .map(Candidate::providerNamespace)
                    .distinct()
                    .sorted()
                    .toList();
        }

        /**
         * Returns the block variants contributed by one provider.
         *
         * @param providerNamespace exact provider namespace to select
         * @return immutable candidate list in lexical block-ID order
         */
        public List<Candidate> candidatesForProvider(String providerNamespace) {
            String provider = matching(providerNamespace, NAMESPACE, "provider namespace");
            return candidates.stream()
                    .filter(candidate -> candidate.providerNamespace().equals(provider))
                    .toList();
        }

        /**
         * Reports whether any candidate used a non-material-specific fallback during discovery.
         *
         * @return {@code true} when the family should be checked before accepting inferred material identity
         */
        public boolean hasFallbackCandidates() {
            return candidates.stream().anyMatch(Candidate::identifiedByFallback);
        }
    }

    /**
     * One installed block proposed as an output variant for an imported ore rule.
     *
     * @param blockId exact output block registry ID
     * @param replaceTag replacement/host tag ID, empty only for review-required candidates
     * @param hostKind inferred host family or review-required state
     * @param evidence strongest reason the block was classified as an ore
     * @param sourceTags bounded conventional tags supporting discovery, sorted lexically
     */
    public record Candidate(
            String blockId, String replaceTag, HostKind hostKind, Evidence evidence, List<String> sourceTags) {
        /**
         * Normalizes resource IDs, fills safe host defaults, and stores distinct source tags in lexical order.
         *
         * @param blockId exact output block registry ID
         * @param replaceTag replacement tag with or without {@code #}, or blank for inferred/default handling
         * @param hostKind inferred host family
         * @param evidence discovery evidence
         * @param sourceTags bounded supporting tag IDs
         */
        public Candidate {
            blockId = resourceId(blockId, "candidate block id");
            Objects.requireNonNull(hostKind, "hostKind");
            Objects.requireNonNull(evidence, "evidence");
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

        /**
         * Reports whether the candidate must be reviewed instead of being automatically imported.
         *
         * @return {@code true} when no safe replacement/host tag was inferred
         */
        public boolean reviewRequired() {
            return hostKind == HostKind.REVIEW_REQUIRED;
        }

        /**
         * Returns the namespace of the mod that registered this exact block variant.
         *
         * @return validated registry namespace parsed from {@link #blockId()}
         */
        public String providerNamespace() {
            return blockId.substring(0, blockId.indexOf(':'));
        }

        /**
         * Reports whether material identity came from broad tags or registry-path inference.
         *
         * <p>The inferred host remains available separately through {@link #hostKind()}, allowing clients to present
         * provider and stone/deepslate variants without conflating them with identification confidence.
         *
         * @return {@code true} unless a material-specific {@code c:ores/*} tag supplied the identity
         */
        public boolean identifiedByFallback() {
            return evidence != Evidence.CONVENTIONAL_TAG;
        }
    }

    /** Ordered strengths of evidence used to identify an installed block as an ore candidate. */
    public enum Evidence {
        /** The registry path resembles {@code *_ore} or {@code ore_*} without supporting common tags. */
        ORE_LIKE_NAME(0),
        /** The block belongs to the broad {@code c:ores} tag. */
        COMMON_ORES_TAG(1),
        /** The block belongs to a material-specific {@code c:ores/*} tag. */
        CONVENTIONAL_TAG(2);

        private final int strength;

        Evidence(int strength) {
            this.strength = strength;
        }

        /**
         * Returns the deterministic comparison rank used while merging discovery evidence.
         *
         * @return non-negative rank, where a larger value is stronger evidence
         */
        public int strength() {
            return strength;
        }

        /**
         * Chooses the stronger of two evidence values, retaining {@code left} when strengths tie.
         *
         * @param left first non-null evidence value
         * @param right second non-null evidence value
         * @return stronger evidence according to {@link #strength()}
         */
        public static Evidence strongest(Evidence left, Evidence right) {
            return left.strength >= right.strength ? left : right;
        }
    }

    /** Conservative inferred host family used to choose an ore replacement tag. */
    public enum HostKind {
        /** Conventional stone-hosted variant. */
        STONE("minecraft:stone_ore_replaceables"),
        /** Conventional deepslate-hosted variant. */
        DEEPSLATE("minecraft:deepslate_ore_replaceables"),
        /** Ambiguous or unsupported host that cannot be imported automatically. */
        REVIEW_REQUIRED("");

        private final String replaceTag;

        HostKind(String replaceTag) {
            this.replaceTag = replaceTag;
        }

        /**
         * Returns the inferred replacement block tag without a leading {@code #}.
         *
         * @return vanilla replacement tag, or an empty string when explicit review is required
         */
        public String replaceTag() {
            return replaceTag;
        }
    }

    /**
     * Deterministic preview row explaining how one selected ore family affects the proposed profile.
     *
     * @param groupId selected ore-family group ID
     * @param status planning outcome for the group
     * @param ruleId generated rule ID, or an empty string when no rule is added
     * @param addedBlocks exact block IDs added to the proposed rule, sorted lexically
     * @param skippedBlocks covered or review-required block IDs, sorted lexically
     * @param message bounded localized message encoding that explains the outcome
     */
    public record DiffEntry(
            String groupId,
            DiffStatus status,
            String ruleId,
            List<String> addedBlocks,
            List<String> skippedBlocks,
            String message) {
        /**
         * Validates IDs, stores immutable sorted block lists, and bounds the explanatory message.
         *
         * @param groupId selected ore-family group ID
         * @param status planning outcome
         * @param ruleId generated rule ID or blank
         * @param addedBlocks added exact block IDs
         * @param skippedBlocks skipped exact block IDs
         * @param message localized explanatory message, or {@code null} for none
         */
        public DiffEntry(
                String groupId,
                DiffStatus status,
                @Nullable String ruleId,
                @Nullable List<String> addedBlocks,
                @Nullable List<String> skippedBlocks,
                @Nullable String message) {
            groupId = resourceId(groupId, "diff group id");
            Objects.requireNonNull(status, "status");
            ruleId = optionalSimpleId(ruleId, "diff rule id");
            addedBlocks = blockIds(addedBlocks, "added blocks");
            skippedBlocks = blockIds(skippedBlocks, "skipped blocks");
            message = message == null ? "" : message.trim();
            if (message.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException("Diff message exceeds " + MAX_MESSAGE_LENGTH + " characters");
            }
            this.groupId = groupId;
            this.status = status;
            this.ruleId = ruleId;
            this.addedBlocks = addedBlocks;
            this.skippedBlocks = skippedBlocks;
            this.message = message;
        }

        private static List<String> blockIds(@Nullable List<String> values, String label) {
            return bounded(values, MAX_ENABLED_CANDIDATES_PER_RULE, label).stream()
                    .map(value -> resourceId(value, label))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    /** Outcome of applying one selected ore-family group to a proposed profile copy. */
    public enum DiffStatus {
        /** Every candidate was safe and uncovered, so a complete new rule was proposed. */
        ADDED,
        /** A rule was proposed for safe uncovered candidates while other candidates were skipped. */
        PARTIALLY_ADDED,
        /** No rule was needed because existing exact or tag targets already cover all safe candidates. */
        SKIPPED_COVERED,
        /** No safe uncovered candidate remained because one or more hosts require explicit review. */
        SKIPPED_REVIEW_REQUIRED
    }

    /**
     * Finite, non-negative workload for one terrain mode.
     *
     * @param attemptsPerChunk conservative placement attempts per eligible chunk
     * @param workUnits conservative block-placement work units per eligible chunk
     */
    public record TerrainWorkload(double attemptsPerChunk, double workUnits) {
        /** Reusable workload with no placement attempts or block work. */
        public static final TerrainWorkload ZERO = new TerrainWorkload(0.0D, 0.0D);

        /**
         * Validates finite, non-negative workload metrics.
         *
         * @param attemptsPerChunk placement attempts per eligible chunk
         * @param workUnits block-placement work units per eligible chunk
         */
        public TerrainWorkload {
            if (!Double.isFinite(attemptsPerChunk)
                    || attemptsPerChunk < 0.0D
                    || !Double.isFinite(workUnits)
                    || workUnits < 0.0D) {
                throw new IllegalArgumentException("Ore workload values must be finite and non-negative");
            }
        }
    }

    /**
     * Complete immutable workload mapping for every terrain mode.
     *
     * @param byTerrain partial or complete workload map; missing and null values become {@link TerrainWorkload#ZERO}
     */
    public record Workload(Map<TerrainMode, TerrainWorkload> byTerrain) {
        /**
         * Copies the supplied values into a complete immutable enum map in terrain declaration order.
         *
         * @param byTerrain partial or complete terrain workload map, or {@code null}
         */
        public Workload(@Nullable Map<TerrainMode, @Nullable TerrainWorkload> byTerrain) {
            EnumMap<TerrainMode, TerrainWorkload> normalized = new EnumMap<>(TerrainMode.class);
            for (TerrainMode terrain : TerrainMode.values()) {
                normalized.put(
                        terrain,
                        byTerrain == null
                                ? TerrainWorkload.ZERO
                                : Objects.requireNonNullElse(byTerrain.get(terrain), TerrainWorkload.ZERO));
            }
            this.byTerrain = Collections.unmodifiableMap(normalized);
        }

        /**
         * Returns the workload for a terrain from the normalized complete map.
         *
         * @param terrain terrain mode to look up
         * @return non-null per-chunk workload
         */
        public TerrainWorkload forTerrain(TerrainMode terrain) {
            return Objects.requireNonNull(
                    byTerrain.get(Objects.requireNonNull(terrain, "terrain")), "complete terrain workload map");
        }
    }

    /**
     * Read-only ore-import preview; constructing this value does not save, activate, or overwrite configuration.
     *
     * @param baseProfileId identifier of the exact source profile used to build the preview
     * @param proposedProfile immutable profile copy containing proposed rules
     * @param diff bounded deterministic group-by-group changes
     * @param beforeWorkload workload of the base profile
     * @param afterWorkload workload of the proposed profile
     * @param validation validation report for the complete proposed profile
     */
    public record Plan(
            String baseProfileId,
            OreProfileDocument proposedProfile,
            List<DiffEntry> diff,
            Workload beforeWorkload,
            Workload afterWorkload,
            ValidationReport validation) {
        /**
         * Validates the base ID, copies the bounded diff, and requires all immutable preview products.
         *
         * @param baseProfileId source profile identifier
         * @param proposedProfile proposed immutable profile copy
         * @param diff deterministic bounded preview rows
         * @param beforeWorkload source-profile workload
         * @param afterWorkload proposed-profile workload
         * @param validation full proposed-profile validation report
         */
        public Plan {
            baseProfileId = matching(baseProfileId, PROFILE_ID, "base profile id");
            Objects.requireNonNull(proposedProfile, "proposedProfile");
            diff = bounded(diff, MAX_DIFF_ENTRIES, "ore import diff entries");
            Objects.requireNonNull(beforeWorkload, "beforeWorkload");
            Objects.requireNonNull(afterWorkload, "afterWorkload");
            Objects.requireNonNull(validation, "validation");
        }

        /**
         * Reports whether the proposed profile passes the final validation report.
         *
         * @return {@code true} only when the complete proposed profile is valid
         */
        public boolean valid() {
            return validation.valid();
        }

        /**
         * Counts diff rows that add a complete or partial rule proposal.
         *
         * @return number of proposed new ore rules
         */
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

    private static String optionalSimpleId(@Nullable String value, String label) {
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

    private static <T> List<T> bounded(@Nullable List<T> values, int maximum, String label) {
        List<T> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > maximum) {
            throw new IllegalArgumentException(label + " exceed the limit of " + maximum);
        }
        return copy;
    }
}
