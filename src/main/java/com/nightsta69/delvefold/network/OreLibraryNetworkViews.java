package com.nightsta69.delvefold.network;

import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryResult;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportPlanner;
import com.nightsta69.delvefold.config.importer.OreImportRegistry;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import com.nightsta69.delvefold.network.model.OreLibraryView.Family;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Projects a retained server discovery catalog and complete active profile into bounded library pages. */
public final class OreLibraryNetworkViews {
    private OreLibraryNetworkViews() {}

    /**
     * Builds one filtered page while calculating configured state from every active-profile rule.
     *
     * @param catalogToken opaque player-bound token for the retained discovery catalog
     * @param discovery retained immutable discovery result
     * @param profile complete active profile, never a paginated administration snapshot
     * @param registry registry snapshot matching the discovery token
     * @param query server-side material, provider, or block-ID search query
     * @param showConfigured whether configured families should remain visible
     * @param requestedPage requested zero-based page, clamped after global filtering
     * @return immutable protocol-bounded page
     */
    public static OreLibraryView page(
            String catalogToken,
            DiscoveryResult discovery,
            OreProfileDocument profile,
            OreImportRegistry registry,
            String query,
            boolean showConfigured,
            int requestedPage) {
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(registry, "registry");
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<String> coveredBlocks = coveredBlocks(profile, registry);
        Map<String, String> suggestedRuleIds = suggestedRuleIds(discovery, profile);
        List<Family> filtered = new ArrayList<>();
        for (Group group : discovery.groups().stream()
                .sorted(Comparator.comparing(Group::id))
                .toList()) {
            boolean configured =
                    group.candidates().stream().map(Candidate::blockId).anyMatch(coveredBlocks::contains);
            if ((!showConfigured && configured) || !matches(group, normalizedQuery)) {
                continue;
            }
            List<Candidate> preferred = OreImportPlanner.preferredCandidates(group);
            Candidate representative = (preferred.isEmpty() ? group.candidates() : preferred)
                    .stream()
                            .min(Comparator.comparingInt(OreLibraryNetworkViews::iconHostOrder)
                                    .thenComparing(Candidate::blockId))
                            .orElseThrow();
            long providerCount = group.candidates().stream()
                    .map(Candidate::providerNamespace)
                    .distinct()
                    .count();
            int candidateCount =
                    Math.min(group.candidates().size(), ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY);
            int importableCount = (int) group.candidates().stream()
                    .filter(candidate -> !candidate.reviewRequired())
                    .limit(ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY)
                    .count();
            filtered.add(new Family(
                    group.id(),
                    group.material(),
                    Objects.requireNonNull(suggestedRuleIds.get(group.id()), "suggested rule ID"),
                    representative.blockId(),
                    representative.hostKind(),
                    Math.toIntExact(Math.min(providerCount, ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY)),
                    candidateCount,
                    importableCount,
                    group.evidence(),
                    configured,
                    group.reviewRequired(),
                    group.candidates().size() > ProtocolLimits.MAX_VARIANTS));
        }

        int total = filtered.size();
        int pageCount = Math.max(
                1,
                (total + ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE - 1)
                        / ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        int start = page * ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE;
        int end = Math.min(total, start + ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE);
        return new OreLibraryView(
                catalogToken,
                profile.revision(),
                page,
                pageCount,
                total,
                normalizedQuery,
                showConfigured,
                discovery.truncated(),
                filtered.subList(start, end));
    }

    private static boolean matches(Group group, String query) {
        if (query.isEmpty()
                || group.id().toLowerCase(Locale.ROOT).contains(query)
                || group.material().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return group.candidates().stream()
                .anyMatch(candidate ->
                        candidate.blockId().toLowerCase(Locale.ROOT).contains(query)
                                || candidate
                                        .providerNamespace()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query));
    }

    private static int iconHostOrder(Candidate candidate) {
        return switch (candidate.hostKind()) {
            case STONE -> 0;
            case DEEPSLATE -> 1;
            case NETHERRACK -> 2;
            case END_STONE -> 3;
            case REVIEW_REQUIRED -> 4;
        };
    }

    private static Set<String> coveredBlocks(OreProfileDocument profile, OreImportRegistry registry) {
        Set<String> covered = new HashSet<>();
        for (OreRule rule : profile.rules()) {
            for (OreTarget target : rule.targets()) {
                if (target.tagDriven()) {
                    covered.addAll(registry.tagMembers(target.blockTag()));
                } else if (!target.block().isBlank()) {
                    covered.add(target.block());
                }
            }
        }
        return covered;
    }

    private static Map<String, String> suggestedRuleIds(DiscoveryResult discovery, OreProfileDocument profile) {
        Set<String> used = new HashSet<>();
        profile.rules().forEach(rule -> used.add(rule.id()));
        return OreImportPlanner.uniqueRuleIds(discovery.groups(), used);
    }
}
