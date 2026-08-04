package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryResult;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.importer.OreImportPlanner;
import com.nightsta69.delvefold.config.importer.OreImportRegistry;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OreLibraryNetworkViewsTest {
    private static final String STONE_REPLACEABLES = "minecraft:stone_ore_replaceables";

    @Test
    void configuredFamiliesAreFilteredAgainstTheWholeProfileBeforePaging() {
        DiscoveryResult discovery = discovery(43);
        FakeRegistry registry = new FakeRegistry(Map.of("c:ores/material_20", List.of(blockId(20))));
        OreProfileDocument profile = profile(
                OreTarget.of(blockId(1), STONE_REPLACEABLES),
                OreTarget.ofTag("c:ores/material_20", STONE_REPLACEABLES));

        OreLibraryView lastPage = OreLibraryNetworkViews.page("token", discovery, profile, registry, "", false, 99);

        assertEquals(7L, lastPage.expectedOreRevision());
        assertEquals(41, lastPage.totalFamilies());
        assertEquals(3, lastPage.pageCount());
        assertEquals(2, lastPage.page());
        assertEquals(11, lastPage.families().size());
        assertTrue(lastPage.families().stream().noneMatch(OreLibraryView.Family::configured));
        assertFalse(lastPage.families().stream()
                .map(OreLibraryView.Family::id)
                .anyMatch(id -> id.equals(familyId(1)) || id.equals(familyId(20))));
    }

    @Test
    void configuredVisibilityMarksExactAndTagCoveredFamilies() {
        DiscoveryResult discovery = discovery(43);
        FakeRegistry registry = new FakeRegistry(Map.of("c:ores/material_20", List.of(blockId(20))));
        OreProfileDocument profile = profile(
                OreTarget.of(blockId(1), STONE_REPLACEABLES),
                OreTarget.ofTag("c:ores/material_20", STONE_REPLACEABLES));

        OreLibraryView exact =
                OreLibraryNetworkViews.page("token", discovery, profile, registry, "material_1", true, 0);
        OreLibraryView tagged =
                OreLibraryNetworkViews.page("token", discovery, profile, registry, "material_20", true, 0);

        assertEquals(11, exact.totalFamilies(), "Material search is deliberately a substring match");
        assertTrue(exact.families().stream()
                .filter(family -> family.id().equals(familyId(1)))
                .allMatch(OreLibraryView.Family::configured));
        assertEquals(1, tagged.totalFamilies());
        assertTrue(tagged.families().getFirst().configured());
    }

    @Test
    void searchMatchesProvidersAndBlockIdsCaseInsensitively() {
        Group special = group(
                90,
                List.of(new Candidate(
                        "specialmod:hidden_signature_ore",
                        STONE_REPLACEABLES,
                        HostKind.STONE,
                        Evidence.CONVENTIONAL_TAG,
                        List.of("c:ores/material_90"))));
        DiscoveryResult discovery = new DiscoveryResult(List.of(group(0), special), false, 2);
        OreProfileDocument profile = profile();

        OreLibraryView provider = OreLibraryNetworkViews.page(
                "token", discovery, profile, new FakeRegistry(Map.of()), "  SPECIALMOD  ", false, 0);
        OreLibraryView blockPath = OreLibraryNetworkViews.page(
                "token", discovery, profile, new FakeRegistry(Map.of()), "hidden_signature", false, 0);

        assertEquals("specialmod", provider.query());
        assertEquals(
                List.of(familyId(90)),
                provider.families().stream().map(OreLibraryView.Family::id).toList());
        assertEquals(
                List.of(familyId(90)),
                blockPath.families().stream().map(OreLibraryView.Family::id).toList());
    }

    @Test
    void providerSearchFindsTheUnifiedMekanismTinFamily() {
        Group tin = new Group(
                "delvefold:ores/tin",
                "delvefold",
                "tin",
                Evidence.CONVENTIONAL_TAG,
                List.of(
                        new Candidate(
                                "mekanism:deepslate_tin_ore",
                                "minecraft:deepslate_ore_replaceables",
                                HostKind.DEEPSLATE,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/tin")),
                        new Candidate(
                                "mekanism:tin_ore",
                                STONE_REPLACEABLES,
                                HostKind.STONE,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/tin"))),
                false);

        OreLibraryView view = OreLibraryNetworkViews.page(
                "token",
                new DiscoveryResult(List.of(tin), false, 2),
                profile(),
                new FakeRegistry(Map.of()),
                "mekanism",
                false,
                0);

        assertEquals(1, view.totalFamilies());
        assertEquals("delvefold:ores/tin", view.families().getFirst().id());
        assertEquals(2, view.families().getFirst().candidateCount());
        assertEquals(2, view.families().getFirst().importableCandidateCount());
        assertFalse(view.families().getFirst().reviewRequired());
    }

    @Test
    void familySummaryCountsProvidersImportableCandidatesAndOverflow() {
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < ProtocolLimits.MAX_VARIANTS + 1; index++) {
            HostKind host = index == 0 ? HostKind.REVIEW_REQUIRED : HostKind.STONE;
            candidates.add(new Candidate(
                    "provider" + index + ":material_0_ore",
                    host.replaceTag(),
                    host,
                    Evidence.CONVENTIONAL_TAG,
                    List.of("c:ores/material_0")));
        }
        DiscoveryResult discovery = new DiscoveryResult(List.of(group(0, candidates)), false, candidates.size());

        OreLibraryView.Family family = OreLibraryNetworkViews.page(
                        "token", discovery, profile(), new FakeRegistry(Map.of()), "", true, 0)
                .families()
                .getFirst();

        assertEquals(ProtocolLimits.MAX_VARIANTS + 1, family.providerCount());
        assertEquals(ProtocolLimits.MAX_VARIANTS + 1, family.candidateCount());
        assertEquals(ProtocolLimits.MAX_VARIANTS, family.importableCandidateCount());
        assertFalse(family.reviewRequired(), "Only the unusual variant should require review");
        assertTrue(family.overflow());
    }

    @Test
    void familyNeedsReviewWhenNoCandidateHasASafeHost() {
        Group reviewOnly = group(
                0,
                List.of(
                        new Candidate(
                                "example:nether_material_0_ore",
                                "",
                                HostKind.REVIEW_REQUIRED,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/material_0")),
                        new Candidate(
                                "other:end_material_0_ore",
                                "",
                                HostKind.REVIEW_REQUIRED,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/material_0"))));

        OreLibraryView.Family family = OreLibraryNetworkViews.page(
                        "token",
                        new DiscoveryResult(List.of(reviewOnly), false, 2),
                        profile(),
                        new FakeRegistry(Map.of()),
                        "",
                        false,
                        0)
                .families()
                .getFirst();

        assertEquals(0, family.importableCandidateCount());
        assertTrue(family.reviewRequired());
    }

    @Test
    void standardStoneVariantIsPreferredForTheFamilyIcon() {
        Group copper = group(
                0,
                List.of(
                        new Candidate(
                                "minecraft:deepslate_copper_ore",
                                "minecraft:deepslate_ore_replaceables",
                                HostKind.DEEPSLATE,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/material_0")),
                        new Candidate(
                                "minecraft:copper_ore",
                                STONE_REPLACEABLES,
                                HostKind.STONE,
                                Evidence.CONVENTIONAL_TAG,
                                List.of("c:ores/material_0"))));

        OreLibraryView.Family family = OreLibraryNetworkViews.page(
                        "token",
                        new DiscoveryResult(List.of(copper), false, 2),
                        profile(),
                        new FakeRegistry(Map.of()),
                        "",
                        false,
                        0)
                .families()
                .getFirst();

        assertEquals("minecraft:copper_ore", family.preferredBlockId());
    }

    @Test
    void suggestedRuleIdsReserveCompleteProfileIdsAndCatalogCollisionsBeforeFiltering() {
        Group slash = new Group(
                "delvefold:ores/copper/a",
                "delvefold",
                "copper/a",
                Evidence.CONVENTIONAL_TAG,
                List.of(new Candidate(
                        "provider:slash_copper_ore",
                        STONE_REPLACEABLES,
                        HostKind.STONE,
                        Evidence.CONVENTIONAL_TAG,
                        List.of("c:ores/copper/a"))),
                false);
        Group underscore = new Group(
                "delvefold:ores/copper_a",
                "delvefold",
                "copper_a",
                Evidence.CONVENTIONAL_TAG,
                List.of(new Candidate(
                        "provider:underscore_copper_ore",
                        STONE_REPLACEABLES,
                        HostKind.STONE,
                        Evidence.CONVENTIONAL_TAG,
                        List.of("c:ores/copper_a"))),
                false);
        DiscoveryResult discovery = new DiscoveryResult(List.of(underscore, slash), false, 2);
        OreProfileDocument profile = profileWithRules(
                rule("delvefold_copper_a", OreTarget.of("provider:unrelated_ore", STONE_REPLACEABLES)),
                rule("delvefold_copper_a_2", OreTarget.of("provider:another_unrelated_ore", STONE_REPLACEABLES)));

        OreLibraryView slashPage = OreLibraryNetworkViews.page(
                "token", discovery, profile, new FakeRegistry(Map.of()), "slash_copper", false, 0);
        OreLibraryView underscorePage = OreLibraryNetworkViews.page(
                "token", discovery, profile, new FakeRegistry(Map.of()), "underscore_copper", false, 0);

        assertEquals("delvefold_copper_a_3", slashPage.families().getFirst().suggestedRuleId());
        assertEquals(
                "delvefold_copper_a_4", underscorePage.families().getFirst().suggestedRuleId());

        var batch = OreImportPlanner.plan(
                profile, List.of(underscore), discovery.groups(), new FakeRegistry(Map.of()), RegistryLookup.SKIP);
        assertEquals(
                underscorePage.families().getFirst().suggestedRuleId(),
                batch.diff().getFirst().ruleId(),
                "A batch containing only a later colliding family must retain its full-catalog displayed suggestion");
    }

    private static DiscoveryResult discovery(int count) {
        return new DiscoveryResult(
                IntStream.range(0, count)
                        .mapToObj(OreLibraryNetworkViewsTest::group)
                        .toList(),
                false,
                count);
    }

    private static Group group(int index) {
        return group(
                index,
                List.of(new Candidate(
                        blockId(index),
                        STONE_REPLACEABLES,
                        HostKind.STONE,
                        Evidence.CONVENTIONAL_TAG,
                        List.of("c:ores/material_" + index))));
    }

    private static Group group(int index, List<Candidate> candidates) {
        return new Group(
                familyId(index), "delvefold", "material_" + index, Evidence.CONVENTIONAL_TAG, candidates, false);
    }

    private static OreProfileDocument profile(OreTarget... targets) {
        List<OreRule> rules = targets.length == 0
                ? List.of()
                : List.of(new OreRule(
                        "configured",
                        true,
                        false,
                        Set.of(),
                        List.of(targets),
                        BiomeFilter.ALL_MINING_BIOMES,
                        List.of()));
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 7L, "test", rules);
    }

    private static OreProfileDocument profileWithRules(OreRule... rules) {
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 7L, "test", List.of(rules));
    }

    private static OreRule rule(String id, OreTarget target) {
        return new OreRule(id, true, false, Set.of(), List.of(target), BiomeFilter.ALL_MINING_BIOMES, List.of());
    }

    private static String familyId(int index) {
        return "delvefold:ores/material_" + index;
    }

    private static String blockId(int index) {
        return "provider" + index + ":material_" + index + "_ore";
    }

    private record FakeRegistry(Map<String, List<String>> members) implements OreImportRegistry {
        @Override
        public List<BlockEntry> blocks() {
            return List.of();
        }

        @Override
        public List<String> tagMembers(String tagId) {
            return members.getOrDefault(tagId, List.of());
        }
    }
}
