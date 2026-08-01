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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds a validated profile-copy preview without mutating or activating any configuration. */
public final class OreImportPlanner {
    private OreImportPlanner() {
    }

    public static Plan plan(
            OreProfileDocument base,
            List<Group> selected,
            OreImportRegistry registry,
            RegistryLookup validationLookup) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(registry, "registry");
        List<Group> safeSelected = selected == null ? List.of() : List.copyOf(selected);
        if (safeSelected.size() > OreImportModels.MAX_SELECTED_GROUPS) {
            throw new IllegalArgumentException(localized(
                    "message.delvefold.import.plan.selection_limit", OreImportModels.MAX_SELECTED_GROUPS));
        }

        // Reject input-order ambiguity and then plan in stable group-ID order.
        TreeMap<String, Group> groups = new TreeMap<>();
        for (Group group : safeSelected) {
            Group previous = groups.putIfAbsent(Objects.requireNonNull(group, "selected group").id(), group);
            if (previous != null && !previous.equals(group)) {
                throw new IllegalArgumentException(localized(
                        "message.delvefold.import.plan.selection_conflict", group.id()));
            }
        }

        List<OreRule> proposedRules = new ArrayList<>(base.rules());
        Set<String> usedRuleIds = new HashSet<>();
        base.rules().forEach(rule -> usedRuleIds.add(rule.id()));
        Set<String> coveredBlocks = coveredBlocks(base, registry);
        List<DiffEntry> diff = new ArrayList<>(groups.size());

        for (Group group : groups.values()) {
            List<Candidate> addedCandidates = new ArrayList<>();
            List<String> covered = new ArrayList<>();
            List<String> reviewRequired = new ArrayList<>();
            for (Candidate candidate : group.candidates()) {
                if (candidate.reviewRequired()) {
                    reviewRequired.add(candidate.blockId());
                } else if (coveredBlocks.contains(candidate.blockId())) {
                    covered.add(candidate.blockId());
                } else {
                    addedCandidates.add(candidate);
                }
            }

            if (addedCandidates.isEmpty()) {
                List<String> skipped = sortedUnion(covered, reviewRequired);
                DiffStatus status = reviewRequired.isEmpty()
                        ? DiffStatus.SKIPPED_COVERED
                        : DiffStatus.SKIPPED_REVIEW_REQUIRED;
                String message = reviewRequired.isEmpty()
                        ? localized("message.delvefold.import.diff_message.covered")
                        : covered.isEmpty()
                                ? localized("message.delvefold.import.diff_message.review_required")
                                : localized("message.delvefold.import.diff_message.covered_and_review");
                diff.add(new DiffEntry(group.id(), status, "", List.of(), skipped, message));
                continue;
            }

            String ruleId = uniqueRuleId(group, usedRuleIds);
            List<OreTarget> targets = addedCandidates.stream()
                    .map(candidate -> OreTarget.of(candidate.blockId(), candidate.replaceTag()))
                    .toList();
            proposedRules.add(OreRuleTemplates.uncommon(ruleId, targets));
            usedRuleIds.add(ruleId);
            addedCandidates.forEach(candidate -> coveredBlocks.add(candidate.blockId()));

            List<String> added = addedCandidates.stream().map(Candidate::blockId).sorted().toList();
            List<String> skipped = sortedUnion(covered, reviewRequired);
            DiffStatus status = skipped.isEmpty() ? DiffStatus.ADDED : DiffStatus.PARTIALLY_ADDED;
            String message = skipped.isEmpty()
                    ? localized("message.delvefold.import.diff_message.added")
                    : localized("message.delvefold.import.diff_message.partially_added");
            diff.add(new DiffEntry(group.id(), status, ruleId, added, skipped, message));
        }

        OreProfileDocument proposed = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION,
                base.revision(),
                base.profile(),
                proposedRules);
        ValidationReport validation = OreConfigValidator.validate(
                proposed, validationLookup == null ? RegistryLookup.SKIP : validationLookup);
        return new Plan(
                base.profile(),
                proposed,
                diff,
                workload(base),
                workload(proposed),
                validation);
    }

    /** Public hook for the whole-profile forecast layer; validation remains the final authority. */
    public static Workload workload(OreProfileDocument profile) {
        Objects.requireNonNull(profile, "profile");
        OreWorkBudgetAnalysis.ProfileBudget budget = OreWorkBudgetAnalysis.analyze(profile);
        Map<TerrainMode, TerrainWorkload> result = new EnumMap<>(TerrainMode.class);
        for (TerrainMode terrain : TerrainMode.values()) {
            OreWorkBudgetAnalysis.Budget terrainBudget = budget.terrain(terrain);
            result.put(terrain, new TerrainWorkload(
                    terrainBudget.attemptsPerChunk(), terrainBudget.workUnitsPerChunk()));
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
                        members.stream().filter(Objects::nonNull).map(String::trim)
                                .filter(value -> !value.isEmpty()).forEach(covered::add);
                    }
                } else if (!target.block().isBlank()) {
                    covered.add(target.block());
                }
            }
        }
        return covered;
    }

    private static String uniqueRuleId(Group group, Set<String> used) {
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
        throw new IllegalStateException(localized(
                "message.delvefold.import.plan.rule_id_failed", group.id()));
    }

    private static String boundedRuleId(String value, String suffix) {
        int maximum = OreConfigValidator.MAX_ID_LENGTH - suffix.length();
        String bounded = value.length() <= maximum ? value : value.substring(0, maximum);
        while (bounded.endsWith(".") || bounded.endsWith("-")) {
            bounded = bounded.substring(0, bounded.length() - 1);
        }
        return bounded.isEmpty() ? "imported_ore" : bounded;
    }

    private static List<String> sortedUnion(List<String> first, List<String> second) {
        Set<String> values = new TreeSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
