package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportDiscovery;
import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryOptions;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.importer.OreImportPlanner;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Adapts shared material-first discovery into the picker presentation model. */
final class OreFamilyCatalog {
    private static final java.util.regex.Pattern WORD_SEPARATOR = java.util.regex.Pattern.compile("_+");

    private OreFamilyCatalog() {}

    /** Discovers the installed client registry with the same pure classifier used by the authoritative server. */
    static Map<String, Group> discoverInstalled() {
        Map<String, Group> discovered =
                OreImportDiscovery.discover(new MinecraftOreImportRegistry(), DiscoveryOptions.INCLUDING_VANILLA)
                        .groups()
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Group::id, group -> group, (first, ignored) -> first, LinkedHashMap::new));
        return Map.copyOf(discovered);
    }

    /** Converts one server summary and its optional matching local group into a picker family. */
    static OreLibraryPickerState.Family present(OreLibraryView.Family summary, @Nullable Group localGroup) {
        List<OreLibraryPickerState.Candidate> candidates =
                localGroup == null ? List.of() : candidatesForWizard(localGroup);
        if (candidates.isEmpty()) {
            candidates = List.of(fallbackCandidate(summary));
        }
        return new OreLibraryPickerState.Family(
                summary.id(),
                summary.material(),
                humanize(summary.material()),
                summary.suggestedRuleId(),
                summary.configured(),
                summary.reviewRequired(),
                candidates);
    }

    /** Returns the namespace of the exact server-selected default representative. */
    static String preferredProvider(OreLibraryView.Family summary) {
        String blockId = summary.preferredBlockId();
        int separator = blockId.indexOf(':');
        return separator <= 0 ? "minecraft" : blockId.substring(0, separator);
    }

    /** Returns bounded provider/host metadata for every candidate shown by the variant editor. */
    static List<OreLibraryPickerState.Candidate> candidatesForWizard(Group group) {
        return orderedCandidates(group).stream()
                .map(OreFamilyCatalog::presentCandidate)
                .toList();
    }

    private static List<Candidate> orderedCandidates(Group group) {
        List<Candidate> preferred = OreImportPlanner.preferredCandidates(group);
        Set<String> seen = new LinkedHashSet<>();
        List<Candidate> ordered = new ArrayList<>();
        for (Candidate candidate : preferred) {
            if (seen.add(candidate.blockId())) {
                ordered.add(candidate);
            }
        }
        group.candidates().stream()
                .sorted(Comparator.comparing(Candidate::providerNamespace).thenComparing(Candidate::blockId))
                .forEach(candidate -> {
                    if (ordered.size() < ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY
                            && seen.add(candidate.blockId())) {
                        ordered.add(candidate);
                    }
                });
        return List.copyOf(ordered);
    }

    private static OreLibraryPickerState.Candidate presentCandidate(Candidate candidate) {
        return new OreLibraryPickerState.Candidate(
                candidate.blockId(),
                candidate.providerNamespace(),
                hostVariant(candidate.hostKind()),
                candidate.replaceTag(),
                candidate.reviewRequired());
    }

    private static OreLibraryPickerState.Candidate fallbackCandidate(OreLibraryView.Family summary) {
        String blockId = summary.preferredBlockId();
        String provider = blockId.substring(0, Math.max(0, blockId.indexOf(':')));
        if (provider.isBlank()) {
            provider = "minecraft";
        }
        HostKind hostKind = summary.preferredHostKind();
        return new OreLibraryPickerState.Candidate(
                blockId, provider, hostVariant(hostKind), hostKind.replaceTag(), hostKind == HostKind.REVIEW_REQUIRED);
    }

    private static String hostVariant(HostKind hostKind) {
        return switch (hostKind) {
            case STONE -> "stone";
            case DEEPSLATE -> "deepslate";
            case NETHERRACK -> "nether";
            case END_STONE -> "end";
            case REVIEW_REQUIRED -> "review_required";
        };
    }

    private static String humanize(String material) {
        String[] words = WORD_SEPARATOR.split(material.replace('/', '_'), -1);
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.isEmpty() ? material : result.toString();
    }
}
