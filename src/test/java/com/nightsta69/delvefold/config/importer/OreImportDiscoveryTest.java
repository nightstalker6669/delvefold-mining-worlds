package com.nightsta69.delvefold.config.importer;

import static com.nightsta69.delvefold.config.importer.FakeOreImportRegistry.block;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryOptions;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OreImportDiscoveryTest {
    @Test
    void conventionalTagsGroupStoneAndDeepslateAcrossProviders() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:deepslate_tin_ore", "c:ores", "c:ores/tin"),
                block("other:tin_ore", "c:ores", "c:ores/tin"),
                block("example:tin_ore", "c:ores", "c:ores/tin"),
                block("minecraft:iron_ore", "c:ores", "c:ores/iron"));

        var result = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY);

        assertEquals(
                List.of("delvefold:ores/tin"),
                result.groups().stream().map(Group::id).toList());
        Group example = result.groups().getFirst();
        assertEquals("delvefold", example.namespace());
        assertEquals("tin", example.material());
        assertEquals(Evidence.CONVENTIONAL_TAG, example.evidence());
        assertFalse(example.reviewRequired());
        assertFalse(example.hasFallbackCandidates());
        assertEquals(List.of("example", "other"), example.providerNamespaces());
        assertEquals(
                List.of("example:deepslate_tin_ore", "example:tin_ore", "other:tin_ore"),
                example.candidates().stream().map(Candidate::blockId).toList());
        assertEquals(HostKind.DEEPSLATE, example.candidates().get(0).hostKind());
        assertEquals(
                "minecraft:deepslate_ore_replaceables",
                example.candidates().get(0).replaceTag());
        assertEquals(HostKind.STONE, example.candidates().get(1).hostKind());
        assertEquals(
                "minecraft:stone_ore_replaceables", example.candidates().get(1).replaceTag());
        assertEquals(
                List.of("example:deepslate_tin_ore", "example:tin_ore"),
                example.candidatesForProvider("example").stream()
                        .map(Candidate::blockId)
                        .toList());
        assertEquals("other", example.candidates().get(2).providerNamespace());
    }

    @Test
    void vanillaIsOptInAndJoinsTheSameLogicalFamily() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("minecraft:iron_ore", "c:ores/iron"), block("example:iron_ore", "c:ores/iron"));

        assertEquals(
                List.of("delvefold:ores/iron"),
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .map(Group::id)
                        .toList());
        Group includingVanilla = OreImportDiscovery.discover(registry, DiscoveryOptions.INCLUDING_VANILLA)
                .groups()
                .getFirst();
        assertEquals("delvefold:ores/iron", includingVanilla.id());
        assertEquals(List.of("example", "minecraft"), includingVanilla.providerNamespaces());
        assertEquals(2, includingVanilla.candidates().size());
    }

    @Test
    void commonCopperTagUnifiesVanillaAndMultipleModProviders() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("minecraft:copper_ore", "c:ores/copper"),
                block("minecraft:deepslate_copper_ore", "c:ores/copper"),
                block("thermal:copper_ore", "c:ores/copper"),
                block("mekanism:deepslate_copper_ore", "c:ores/copper"));

        Group copper = OreImportDiscovery.discover(registry, DiscoveryOptions.INCLUDING_VANILLA)
                .groups()
                .getFirst();

        assertEquals("delvefold:ores/copper", copper.id());
        assertEquals(List.of("mekanism", "minecraft", "thermal"), copper.providerNamespaces());
        assertEquals(4, copper.candidates().size());
        assertEquals(2, copper.candidatesForProvider("minecraft").size());
        assertFalse(copper.reviewRequired());
        assertFalse(copper.hasFallbackCandidates());
    }

    @Test
    void mekanismBaseTinVariantsFormOneReadyStoneAndDeepslateFamily() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("mekanism:tin_ore", "c:ores", "c:ores/tin"),
                block("mekanism:deepslate_tin_ore", "c:ores", "c:ores/tin"));

        Group tin = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();

        assertEquals("delvefold:ores/tin", tin.id());
        assertEquals(List.of("mekanism"), tin.providerNamespaces());
        assertEquals(
                List.of("mekanism:deepslate_tin_ore", "mekanism:tin_ore"),
                tin.candidates().stream().map(Candidate::blockId).toList());
        assertEquals(HostKind.DEEPSLATE, tin.candidates().get(0).hostKind());
        assertEquals(HostKind.STONE, tin.candidates().get(1).hostKind());
        assertFalse(tin.reviewRequired());
    }

    @Test
    void nameFallbackIsStrictEnoughToAvoidContainsOreFalsePositives() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:lead_ore"),
                block("example:ore_lead"),
                block("example:orechid"),
                block("example:core_block"));

        var result = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY);

        assertEquals(1, result.groups().size());
        Group lead = result.groups().getFirst();
        assertEquals("delvefold:ores/lead", lead.id());
        assertEquals(Evidence.ORE_LIKE_NAME, lead.evidence());
        assertFalse(lead.reviewRequired());
        assertTrue(lead.hasFallbackCandidates());
        assertEquals(
                List.of("example:lead_ore", "example:ore_lead"),
                lead.candidates().stream().map(Candidate::blockId).toList());
    }

    @Test
    void conventionalEvidenceWinsAndExplicitNetherHostsImportWithoutReview() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:tin_ore", "c:ores/copper", "c:ores/tin"),
                block("example:stone_tin_ore", "c:ores/tin"),
                block("example:nether_tin_ore", "c:ores/tin"),
                block("example:tin_cluster", "c:ores/tin"));

        Group group = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();
        Map<String, Candidate> candidates =
                group.candidates().stream().collect(Collectors.toMap(Candidate::blockId, Function.identity()));
        Candidate tinOre = requireNonNull(candidates.get("example:tin_ore"));
        Candidate stoneTinOre = requireNonNull(candidates.get("example:stone_tin_ore"));
        Candidate netherTinOre = requireNonNull(candidates.get("example:nether_tin_ore"));
        Candidate tinCluster = requireNonNull(candidates.get("example:tin_cluster"));

        assertEquals("delvefold:ores/tin", group.id());
        assertFalse(group.reviewRequired());
        assertEquals(HostKind.STONE, tinOre.hostKind());
        assertEquals(HostKind.STONE, stoneTinOre.hostKind());
        assertEquals(HostKind.NETHERRACK, netherTinOre.hostKind());
        assertEquals("c:netherracks", netherTinOre.replaceTag());
        assertEquals(HostKind.REVIEW_REQUIRED, tinCluster.hostKind());
        assertEquals("", tinCluster.replaceTag());
    }

    @Test
    void aggregateCommonTagAloneDoesNotJustifyGuessingAHost() {
        FakeOreImportRegistry registry =
                FakeOreImportRegistry.of(block("example:tin_cluster", "c:ores"), block("example:lead_ore", "c:ores"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));
        Group tinCluster = requireNonNull(groups.get("delvefold:ores/tin_cluster"));
        Group lead = requireNonNull(groups.get("delvefold:ores/lead"));

        assertEquals(
                HostKind.REVIEW_REQUIRED, tinCluster.candidates().getFirst().hostKind());
        assertEquals(HostKind.STONE, lead.candidates().getFirst().hostKind());
        assertTrue(tinCluster.reviewRequired());
        assertFalse(lead.reviewRequired());
    }

    @Test
    void resultIsDeterministicAndDuplicateEntriesMergeBeforeGrouping() {
        List<OreImportRegistry.BlockEntry> entries = new ArrayList<>(List.of(
                block("zinc:zinc_ore", "c:ores/zinc"),
                block("example:tin_ore", "c:ores"),
                block("example:tin_ore", "c:ores/tin"),
                block("example:deepslate_tin_ore", "c:ores/tin")));
        var forward = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);
        Collections.reverse(entries);
        var reverse = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);

        assertEquals(forward, reverse);
        assertEquals(2, forward.groups().getFirst().candidates().size());
        assertEquals(Evidence.CONVENTIONAL_TAG, forward.groups().getFirst().evidence());
    }

    @Test
    void materialSpecificTagsTakePriorityAndAbsorbMatchingFallbackProviders() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("oddity:nether_radioactive_crystal_ore", "c:ores/uranium"),
                block("fallback:uranium_ore"),
                block("other:copper_ore", "c:ores/copper"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));
        Group uranium = requireNonNull(groups.get("delvefold:ores/uranium"));

        assertEquals(List.of("fallback", "oddity"), uranium.providerNamespaces());
        assertEquals(
                List.of("fallback:uranium_ore", "oddity:nether_radioactive_crystal_ore"),
                uranium.candidates().stream().map(Candidate::blockId).toList());
        assertEquals(Evidence.CONVENTIONAL_TAG, uranium.evidence());
        assertFalse(uranium.reviewRequired(), "A safe canonical variant keeps the family batch-ready");
        assertTrue(uranium.hasFallbackCandidates());
        assertEquals(HostKind.STONE, uranium.candidates().get(0).hostKind());
        assertEquals(HostKind.REVIEW_REQUIRED, uranium.candidates().get(1).hostKind());
    }

    @Test
    void conservativeNameFallbackKeepsQuartzAndCertusQuartzSeparate() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("alpha:quartz_ore"),
                block("beta:deepslate_quartz_ore"),
                block("ae2:certus_quartz_ore"),
                block("ae2:deepslate_certus_quartz_ore"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));

        assertEquals(Set.of("delvefold:ores/certus_quartz", "delvefold:ores/quartz"), groups.keySet());
        assertEquals(
                List.of("ae2:certus_quartz_ore", "ae2:deepslate_certus_quartz_ore"),
                requireNonNull(groups.get("delvefold:ores/certus_quartz")).candidates().stream()
                        .map(Candidate::blockId)
                        .toList());
        assertEquals(
                List.of("alpha:quartz_ore", "beta:deepslate_quartz_ore"),
                requireNonNull(groups.get("delvefold:ores/quartz")).candidates().stream()
                        .map(Candidate::blockId)
                        .toList());
        assertTrue(groups.values().stream().noneMatch(Group::reviewRequired));
    }

    @Test
    void missingTagsStillGroupCanonicalMaterialNamesWithoutReview() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("alpha:copper_ore"),
                block("beta:ore_copper"),
                block("gamma:stone_copper_ore"),
                block("gamma:deepslate_copper_ore"));

        Group copper = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();

        assertEquals("delvefold:ores/copper", copper.id());
        assertEquals(List.of("alpha", "beta", "gamma"), copper.providerNamespaces());
        assertEquals(4, copper.candidates().size());
        assertFalse(copper.reviewRequired());
        assertTrue(copper.candidates().stream().allMatch(Candidate::identifiedByFallback));
    }

    @Test
    void materialTaggedNetherAndEndAliasesJoinTheBaseMaterialWithPreciseHosts() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:garnet_ore", "c:ores/garnet"),
                block("example:deepslate_garnet_ore", "c:ores/garnet"),
                block("example:nether_garnet_ore", "c:ores/garnet"),
                block("example:garnet_nether_ore", "c:ores/garnet"),
                block("example:netherrack_garnet_ore", "c:ores/garnet"),
                block("example:garnet_netherrack_ore", "c:ores/garnet"),
                block("example:end_garnet_ore", "c:ores/garnet"),
                block("example:garnet_end_ore", "c:ores/garnet"),
                block("example:endstone_garnet_ore", "c:ores/garnet"),
                block("example:garnet_endstone_ore", "c:ores/garnet"),
                block("example:end_stone_garnet_ore", "c:ores/garnet"),
                block("example:garnet_end_stone_ore", "c:ores/garnet"));

        Group garnet = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();
        Map<String, Candidate> candidates =
                garnet.candidates().stream().collect(Collectors.toMap(Candidate::blockId, Function.identity()));

        assertEquals("delvefold:ores/garnet", garnet.id());
        assertEquals(12, garnet.candidates().size());
        assertFalse(garnet.reviewRequired());
        assertEquals(
                HostKind.NETHERRACK,
                requireNonNull(candidates.get("example:nether_garnet_ore")).hostKind());
        assertEquals(
                HostKind.NETHERRACK,
                requireNonNull(candidates.get("example:netherrack_garnet_ore")).hostKind());
        assertEquals(
                HostKind.END_STONE,
                requireNonNull(candidates.get("example:end_garnet_ore")).hostKind());
        assertEquals(
                HostKind.END_STONE,
                requireNonNull(candidates.get("example:endstone_garnet_ore")).hostKind());
        assertEquals(
                HostKind.END_STONE,
                requireNonNull(candidates.get("example:end_stone_garnet_ore")).hostKind());
        assertTrue(candidates.values().stream()
                .filter(candidate -> candidate.hostKind() == HostKind.NETHERRACK)
                .allMatch(candidate -> candidate.replaceTag().equals("c:netherracks")));
        assertTrue(candidates.values().stream()
                .filter(candidate -> candidate.hostKind() == HostKind.END_STONE)
                .allMatch(candidate -> candidate.replaceTag().equals("c:end_stones")));
        assertEquals(
                4,
                candidates.values().stream()
                        .filter(candidate -> candidate.hostKind() == HostKind.NETHERRACK)
                        .count());
        assertEquals(
                6,
                candidates.values().stream()
                        .filter(candidate -> candidate.hostKind() == HostKind.END_STONE)
                        .count());
    }

    @Test
    void untaggedDimensionAliasesStayInSeparateReviewFamiliesWithoutGuessingMaterialIdentity() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:garnet_ore"), block("example:nether_garnet_ore"), block("example:garnet_end_stone_ore"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));

        assertEquals(
                Set.of("delvefold:ores/garnet", "delvefold:ores/nether_garnet", "delvefold:ores/garnet_end_stone"),
                groups.keySet());
        Group garnet = requireNonNull(groups.get("delvefold:ores/garnet"));
        assertFalse(garnet.reviewRequired(), "The canonical stone variant keeps the family batch-ready");
        assertEquals(HostKind.STONE, garnet.candidates().getFirst().hostKind());
        assertTrue(requireNonNull(groups.get("delvefold:ores/nether_garnet")).reviewRequired());
        assertTrue(requireNonNull(groups.get("delvefold:ores/garnet_end_stone")).reviewRequired());
    }

    @Test
    void untaggedMaterialNamesBeginningWithDimensionWordsNeverMergeIntoOtherMaterials() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:end_steel_ore"),
                block("example:steel_ore"),
                block("example:nether_star_ore"),
                block("example:star_ore"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));

        assertEquals(
                Set.of(
                        "delvefold:ores/end_steel",
                        "delvefold:ores/steel",
                        "delvefold:ores/nether_star",
                        "delvefold:ores/star"),
                groups.keySet());
        assertTrue(requireNonNull(groups.get("delvefold:ores/end_steel")).reviewRequired());
        assertTrue(requireNonNull(groups.get("delvefold:ores/nether_star")).reviewRequired());
        assertFalse(requireNonNull(groups.get("delvefold:ores/steel")).reviewRequired());
        assertFalse(requireNonNull(groups.get("delvefold:ores/star")).reviewRequired());
    }

    @Test
    void materialTagIdentityWinsWhenAMaterialNameStartsWithAHostWord() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:end_steel_ore", "c:ores/end_steel"),
                block("example:end_end_steel_ore", "c:ores/end_steel"));

        Group endSteel = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();
        Map<String, Candidate> candidates =
                endSteel.candidates().stream().collect(Collectors.toMap(Candidate::blockId, Function.identity()));

        assertEquals("delvefold:ores/end_steel", endSteel.id());
        assertEquals(
                HostKind.STONE,
                requireNonNull(candidates.get("example:end_steel_ore")).hostKind());
        assertEquals(
                HostKind.END_STONE,
                requireNonNull(candidates.get("example:end_end_steel_ore")).hostKind());
    }

    @Test
    void unusualHostPrefixesRemainExplicitCandidateReviewItems() {
        List<String> prefixes = List.of(
                "blackstone_", "basalt_", "soul_", "sand_", "gravel_", "tuff_", "granite_", "diorite_", "andesite_");
        List<OreImportRegistry.BlockEntry> entries = prefixes.stream()
                .flatMap(prefix -> java.util.stream.Stream.of(
                        block("example:" + prefix + "tin_ore", "c:ores/tin"),
                        block("example:tin_" + prefix + "ore", "c:ores/tin")))
                .toList();

        Group tin = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();

        assertTrue(tin.reviewRequired());
        assertEquals(prefixes.size() * 2, tin.candidates().size());
        assertTrue(tin.candidates().stream().allMatch(Candidate::reviewRequired));
        assertTrue(tin.candidates().stream()
                .allMatch(candidate -> candidate.replaceTag().isBlank()));
    }

    @Test
    void untaggedUnusualHostAffixesNeverBecomeSafeStoneFallbacks() {
        List<String> aliases =
                List.of("blackstone", "basalt", "soul", "sand", "gravel", "tuff", "granite", "diorite", "andesite");
        List<OreImportRegistry.BlockEntry> entries = aliases.stream()
                .flatMap(alias -> java.util.stream.Stream.of(
                        block("example:" + alias + "_tin_ore"), block("example:tin_" + alias + "_ore")))
                .toList();

        var result = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);

        assertEquals(aliases.size() * 2, result.groups().size());
        assertTrue(result.groups().stream().allMatch(Group::reviewRequired));
        assertTrue(result.groups().stream()
                .flatMap(group -> group.candidates().stream())
                .allMatch(candidate -> candidate.hostKind() == HostKind.REVIEW_REQUIRED));
        assertTrue(result.groups().stream()
                .flatMap(group -> group.candidates().stream())
                .allMatch(candidate -> candidate.replaceTag().isBlank()));
    }

    @Test
    void conflictingMaterialTagsWithoutANameMatchNeverCauseAnArbitraryMerge() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:mystery_ore", "c:ores/copper", "c:ores/tin"),
                block("other:copper_ore", "c:ores/copper"),
                block("other:tin_ore", "c:ores/tin"));

        Map<String, Group> groups =
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .collect(Collectors.toMap(Group::id, Function.identity()));

        assertEquals(Set.of("delvefold:ores/copper", "delvefold:ores/mystery", "delvefold:ores/tin"), groups.keySet());
        Group mystery = requireNonNull(groups.get("delvefold:ores/mystery"));
        assertTrue(mystery.reviewRequired());
        assertEquals(Evidence.ORE_LIKE_NAME, mystery.evidence());
        assertEquals(HostKind.REVIEW_REQUIRED, mystery.candidates().getFirst().hostKind());
        assertEquals("", mystery.candidates().getFirst().replaceTag());
        assertEquals(
                List.of("example:mystery_ore"),
                mystery.candidates().stream().map(Candidate::blockId).toList());
    }

    @Test
    void oneAmbiguousCandidateDoesNotMarkASafeMixedFamilyAsWhollyReviewRequired() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("ambiguous:copper_ore", "c:ores/lead", "c:ores/tin"), block("safe:copper_ore"));

        Group copper = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY)
                .groups()
                .getFirst();
        Map<String, Candidate> candidates =
                copper.candidates().stream().collect(Collectors.toMap(Candidate::blockId, Function.identity()));

        assertEquals("delvefold:ores/copper", copper.id());
        assertFalse(copper.reviewRequired());
        assertTrue(requireNonNull(candidates.get("ambiguous:copper_ore")).reviewRequired());
        assertFalse(requireNonNull(candidates.get("safe:copper_ore")).reviewRequired());
    }

    @Test
    void oversizedFamiliesAreTruncatedExplicitlyAtTheModelBoundary() {
        List<OreImportRegistry.BlockEntry> entries = new ArrayList<>();
        for (int index = 0; index < OreImportModels.MAX_DISCOVERED_CANDIDATES_PER_GROUP + 1; index++) {
            entries.add(block("example:variant_" + index + "_ore", "c:ores/tin"));
        }

        var result = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);

        assertTrue(result.truncated());
        assertEquals(
                OreImportModels.MAX_DISCOVERED_CANDIDATES_PER_GROUP,
                result.groups().getFirst().candidates().size());

        Candidate candidate = new Candidate("example:tin_ore", "", HostKind.STONE, Evidence.ORE_LIKE_NAME, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Group(
                        "delvefold:ores/tin",
                        "delvefold",
                        "tin",
                        Evidence.ORE_LIKE_NAME,
                        java.util.Collections.nCopies(
                                OreImportModels.MAX_DISCOVERED_CANDIDATES_PER_GROUP + 1, candidate),
                        false));
        Group bounded =
                new Group("delvefold:ores/tin", "delvefold", "tin", Evidence.ORE_LIKE_NAME, List.of(candidate), false);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreImportModels.DiscoveryResult(
                        java.util.Collections.nCopies(OreImportModels.MAX_GROUPS + 1, bounded), false, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Candidate(
                        "example:tin_ore",
                        "",
                        HostKind.STONE,
                        Evidence.ORE_LIKE_NAME,
                        java.util.Collections.nCopies(
                                OreImportModels.MAX_SOURCE_TAGS_PER_CANDIDATE + 1, "c:ores/tin")));
    }

    @Test
    void discoveryRetainsMoreCandidatesThanOneRuleCanEnable() {
        List<OreImportRegistry.BlockEntry> entries = new ArrayList<>();
        for (int index = 0; index < OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE + 4; index++) {
            entries.add(block("provider" + index + ":copper_ore", "c:ores/copper"));
        }

        var result = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);

        assertFalse(result.truncated());
        assertEquals(
                OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE + 4,
                result.groups().getFirst().candidates().size());
        assertEquals(
                OreImportModels.MAX_ENABLED_CANDIDATES_PER_RULE + 4,
                result.groups().getFirst().providerNamespaces().size());
    }

    @Test
    void candidateSourceTagsAreBoundedAndSorted() {
        Set<String> tags = Set.of(
                "c:ores",
                "c:ores/a",
                "c:ores/b",
                "c:ores/c",
                "c:ores/d",
                "c:ores/e",
                "c:ores/f",
                "c:ores/g",
                "c:ores/h",
                "c:ores/tin");
        var result = OreImportDiscovery.discover(
                new FakeOreImportRegistry(List.of(new OreImportRegistry.BlockEntry("example:tin_ore", tags))),
                DiscoveryOptions.MODDED_ONLY);

        assertTrue(result.truncated());
        List<String> sourceTags =
                result.groups().getFirst().candidates().getFirst().sourceTags();
        assertEquals(OreImportModels.MAX_SOURCE_TAGS_PER_CANDIDATE, sourceTags.size());
        assertEquals(sourceTags.stream().sorted().toList(), sourceTags);
        assertTrue(sourceTags.contains("c:ores/tin"), "The selected material-identity tag must survive detail bounds");
    }
}
