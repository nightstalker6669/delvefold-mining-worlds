package com.nightsta69.delvefold.config.importer;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.OreRuleTemplates;
import com.nightsta69.delvefold.config.analysis.OreWorkBudgetAnalysis;
import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiffEntry;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.Plan;
import com.nightsta69.delvefold.config.importer.OreImportModels.TerrainWorkload;
import com.nightsta69.delvefold.config.importer.OreImportModels.Workload;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Builds a validated profile-copy preview without mutating or activating any configuration. */
public final class OreImportPlanner {
    private OreImportPlanner() {}

    /**
     * Builds a deterministic, validated profile-copy preview without saving, activating, or overwriting a profile.
     *
     * <p>Selections are deduplicated and processed in lexical group-ID order. Any existing exact or expanded-tag target
     * suppresses its whole logical family. For a new family, only the preferred provider's safe stone/deepslate
     * variants are enabled by default: {@code minecraft} when available, otherwise the lexically first provider. Other
     * providers remain in discovery for later toggling, avoiding duplicate ore output by default. Accepted candidates
     * use the existing Uncommon rule template. The proposed document retains the base revision and profile ID; only a
     * later authorized commit may save it under a new name.
     *
     * @param base immutable source profile
     * @param selected groups selected for import, or {@code null} for an empty preview
     * @param registry deterministic registry snapshot used to expand existing output tags
     * @param validationLookup registry lookup for final validation, or {@code null} to skip registry-presence checks
     * @return immutable diff, before/after workload, proposed profile, and validation report
     */
    public static Plan plan(
            OreProfileDocument base,
            @Nullable List<Group> selected,
            OreImportRegistry registry,
            @Nullable RegistryLookup validationLookup) {
        return planInternal(base, selected, selected == null ? List.of() : selected, registry, validationLookup, false);
    }

    /**
     * Builds a preview while reserving generated rule IDs against a complete discovery catalog.
     *
     * <p>This overload is used when a client first sees server-issued suggested IDs for a retained catalog and later
     * submits only a subset of those families. Reserving identifiers for every catalog family ensures the committed
     * rule ID is exactly the suggestion previously displayed, even when distinct family paths normalize to the same
     * rule-ID base. The original four-argument overload retains selected-only reservation semantics for the guided
     * importer.
     *
     * @param base immutable source profile
     * @param selected groups selected for import, or {@code null} for an empty preview
     * @param ruleIdCatalog complete immutable catalog whose IDs must be reserved before planning the selection
     * @param registry deterministic registry snapshot used to expand existing output tags
     * @param validationLookup registry lookup for final validation, or {@code null} to skip registry-presence checks
     * @return immutable diff, before/after workload, proposed profile, and validation report
     */
    public static Plan plan(
            OreProfileDocument base,
            @Nullable List<Group> selected,
            List<Group> ruleIdCatalog,
            OreImportRegistry registry,
            @Nullable RegistryLookup validationLookup) {
        return planInternal(base, selected, ruleIdCatalog, registry, validationLookup, true);
    }

    private static Plan planInternal(
            OreProfileDocument base,
            @Nullable List<Group> selected,
            List<Group> ruleIdCatalog,
            OreImportRegistry registry,
            @Nullable RegistryLookup validationLookup,
            boolean reserveCompleteCatalog) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(ruleIdCatalog, "ruleIdCatalog");
        Objects.requireNonNull(registry, "registry");
        List<Group> safeSelected = selected == null ? List.of() : List.copyOf(selected);
        if (safeSelected.size() > OreImportModels.MAX_SELECTED_GROUPS) {
            throw new IllegalArgumentException(
                    localized("message.delvefold.import.plan.selection_limit", OreImportModels.MAX_SELECTED_GROUPS));
        }

        // Reject input-order ambiguity and then plan in stable group-ID order.
        TreeMap<String, Group> groups = new TreeMap<>();
        for (Group group : safeSelected) {
            Group previous = groups.putIfAbsent(
                    Objects.requireNonNull(group, "selected group").id(), group);
            if (previous != null && !previous.equals(group)) {
                throw new IllegalArgumentException(
                        localized("message.delvefold.import.plan.selection_conflict", group.id()));
            }
        }

        Map<String, Group> catalogGroups = groupsById(ruleIdCatalog);
        for (Group selectedGroup : groups.values()) {
            Group catalogGroup = catalogGroups.get(selectedGroup.id());
            if (!selectedGroup.equals(catalogGroup)) {
                throw new IllegalArgumentException(
                        localized("message.delvefold.import.plan.selection_conflict", selectedGroup.id()));
            }
        }

        List<OreRule> proposedRules = new ArrayList<>(base.rules());
        Set<String> existingRuleIds = base.rules().stream().map(OreRule::id).collect(Collectors.toSet());
        Map<String, String> reservedRuleIds =
                reserveCompleteCatalog ? uniqueRuleIds(List.copyOf(catalogGroups.values()), existingRuleIds) : Map.of();
        Set<String> usedRuleIds = new HashSet<>(existingRuleIds);
        Set<String> coveredBlocks = coveredBlocks(base, registry);
        List<DiffEntry> diff = new ArrayList<>(groups.size());

        for (Group group : groups.values()) {
            boolean familyCovered =
                    group.candidates().stream().map(Candidate::blockId).anyMatch(coveredBlocks::contains);
            if (familyCovered) {
                List<String> skipped = group.candidates().stream()
                        .map(Candidate::blockId)
                        .sorted()
                        .limit(OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE)
                        .toList();
                diff.add(new DiffEntry(
                        group.id(),
                        DiffStatus.SKIPPED_COVERED,
                        "",
                        List.of(),
                        skipped,
                        localized("message.delvefold.import.diff_message.covered")));
                continue;
            }

            List<Candidate> addedCandidates = preferredCandidates(group);
            Set<String> preferredBlocks =
                    addedCandidates.stream().map(Candidate::blockId).collect(Collectors.toSet());
            List<String> skippedCandidates = group.candidates().stream()
                    .map(Candidate::blockId)
                    .filter(blockId -> !preferredBlocks.contains(blockId))
                    .sorted()
                    .limit(OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE)
                    .toList();
            if (addedCandidates.isEmpty()) {
                diff.add(new DiffEntry(
                        group.id(),
                        DiffStatus.SKIPPED_REVIEW_REQUIRED,
                        "",
                        List.of(),
                        skippedCandidates,
                        localized("message.delvefold.import.diff_message.review_required")));
                continue;
            }

            String ruleId = reserveCompleteCatalog
                    ? Objects.requireNonNull(reservedRuleIds.get(group.id()), "reserved rule ID")
                    : uniqueRuleId(group, usedRuleIds);
            List<OreTarget> targets = addedCandidates.stream()
                    .map(candidate -> OreTarget.of(candidate.blockId(), candidate.replaceTag()))
                    .toList();
            proposedRules.add(OreRuleTemplates.uncommon(ruleId, targets));
            usedRuleIds.add(ruleId);
            addedCandidates.forEach(candidate -> coveredBlocks.add(candidate.blockId()));

            List<String> added =
                    addedCandidates.stream().map(Candidate::blockId).sorted().toList();
            List<String> skipped = skippedCandidates;
            DiffStatus status = skipped.isEmpty() ? DiffStatus.ADDED : DiffStatus.PARTIALLY_ADDED;
            String message = skipped.isEmpty()
                    ? localized("message.delvefold.import.diff_message.added")
                    : localized("message.delvefold.import.diff_message.partially_added");
            diff.add(new DiffEntry(group.id(), status, ruleId, added, skipped, message));
        }

        OreProfileDocument proposed = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, base.revision(), base.profile(), proposedRules);
        ValidationReport validation = OreConfigValidator.validate(
                proposed, validationLookup == null ? RegistryLookup.SKIP : validationLookup);
        return new Plan(base.profile(), proposed, diff, workload(base), workload(proposed), validation);
    }

    private static Map<String, Group> groupsById(List<Group> source) {
        TreeMap<String, Group> groups = new TreeMap<>();
        for (Group group : source) {
            Group value = Objects.requireNonNull(group, "ore family");
            Group previous = groups.putIfAbsent(value.id(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException(
                        localized("message.delvefold.import.plan.selection_conflict", value.id()));
            }
        }
        return groups;
    }

    /**
     * Selects the provider variants enabled by default for one logical material family.
     *
     * <p>Review-required hosts are never selected. Vanilla is preferred when it contributes at least one safe variant;
     * otherwise the lexically first provider with a safe variant wins. Safe stone/deepslate candidates belonging to
     * that provider are retained in deterministic block-ID order up to the saved-rule target limit.
     *
     * @param group discovered logical material family
     * @return immutable preferred-provider candidates, or an empty list when all hosts require review
     */
    public static List<Candidate> preferredCandidates(Group group) {
        Objects.requireNonNull(group, "group");
        TreeMap<String, List<Candidate>> safeByProvider = new TreeMap<>();
        for (Candidate candidate : group.candidates()) {
            if (!candidate.reviewRequired()) {
                safeByProvider
                        .computeIfAbsent(candidate.providerNamespace(), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }
        if (safeByProvider.isEmpty()) {
            return List.of();
        }
        String preferredProvider = safeByProvider.containsKey("minecraft") ? "minecraft" : safeByProvider.firstKey();
        return Objects.requireNonNull(safeByProvider.get(preferredProvider), "preferred provider candidates").stream()
                .sorted(Comparator.comparing(Candidate::blockId))
                .limit(OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE)
                .toList();
    }

    /**
     * Computes conservative whole-profile workload while leaving validation as the final acceptance authority.
     *
     * @param profile profile whose enabled rules should be totaled
     * @return immutable attempts and work units per eligible chunk for every terrain mode
     */
    public static Workload workload(OreProfileDocument profile) {
        Objects.requireNonNull(profile, "profile");
        OreWorkBudgetAnalysis.ProfileBudget budget = OreWorkBudgetAnalysis.analyze(profile);
        Map<TerrainMode, TerrainWorkload> result = new EnumMap<>(TerrainMode.class);
        for (TerrainMode terrain : TerrainMode.values()) {
            OreWorkBudgetAnalysis.Budget terrainBudget = budget.terrain(terrain);
            result.put(
                    terrain, new TerrainWorkload(terrainBudget.attemptsPerChunk(), terrainBudget.workUnitsPerChunk()));
        }
        return new Workload(result);
    }

    private static Set<String> coveredBlocks(OreProfileDocument base, OreImportRegistry registry) {
        Set<String> covered = new TreeSet<>();
        for (OreRule rule : base.rules()) {
            for (OreTarget target : rule.targets()) {
                if (target.tagDriven()) {
                    List<String> members = registry.tagMembers(target.blockTag());
                    if (members != null) {
                        members.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .forEach(covered::add);
                    }
                } else if (!target.block().isBlank()) {
                    covered.add(target.block());
                }
            }
        }
        return covered;
    }

    /**
     * Returns the deterministic bounded rule identifier for a group without colliding with any supplied identifier.
     *
     * <p>The returned value is not added to {@code used}. Callers assigning identifiers to multiple groups must add
     * each accepted result before requesting the next one so suffix allocation remains stable.
     *
     * @param group material family whose namespace and material form the base identifier
     * @param used complete set of identifiers that must not be reused
     * @return first available bounded identifier, using {@code _2}, {@code _3}, and later suffixes when necessary
     */
    public static String uniqueRuleId(Group group, Set<String> used) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(used, "used");
        String raw = (group.namespace() + '_' + group.material())
                .toLowerCase(Locale.ROOT)
                .replace('/', '_')
                .replaceAll("[^a-z0-9_.-]", "_")
                .replaceAll("_+", "_");
        String base = boundedRuleId(raw.isBlank() ? "imported_ore" : raw, "");
        if (!used.contains(base)) {
            return base;
        }
        for (int suffix = 2; suffix < Integer.MAX_VALUE; suffix++) {
            String marker = "_" + suffix;
            String candidate = boundedRuleId(base, marker) + marker;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(localized("message.delvefold.import.plan.rule_id_failed", group.id()));
    }

    /**
     * Reserves deterministic collision-safe rule IDs for an entire material-family catalog.
     *
     * <p>Families are deduplicated and processed in lexical group-ID order. The supplied set is defensively copied and
     * is never mutated. Every returned ID is reserved before the next family is considered, so normalization collisions
     * receive stable suffixes independent of filtering, paging, or the subset later selected for import.
     *
     * @param catalog complete discovered family catalog
     * @param used identifiers already present in the destination profile
     * @return immutable mapping from family ID to its reserved rule ID
     */
    public static Map<String, String> uniqueRuleIds(List<Group> catalog, Set<String> used) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(used, "used");
        if (catalog.size() > OreImportModels.MAX_GROUPS) {
            throw new IllegalArgumentException("Too many ore families for rule-ID reservation");
        }
        Set<String> reserved = new HashSet<>(used);
        Map<String, String> result = new LinkedHashMap<>();
        for (Group group : groupsById(catalog).values()) {
            String ruleId = uniqueRuleId(group, reserved);
            result.put(group.id(), ruleId);
            reserved.add(ruleId);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String boundedRuleId(String value, String suffix) {
        int maximum = OreConfigValidator.MAX_ID_LENGTH - suffix.length();
        String bounded = value.length() <= maximum ? value : value.substring(0, maximum);
        while (bounded.endsWith(".") || bounded.endsWith("-")) {
            bounded = bounded.substring(0, bounded.length() - 1);
        }
        return bounded.isEmpty() ? "imported_ore" : bounded;
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
