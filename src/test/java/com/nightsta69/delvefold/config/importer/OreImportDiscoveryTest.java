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
    void conventionalTagsGroupStoneAndDeepslatePerNamespace() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:deepslate_tin_ore", "c:ores", "c:ores/tin"),
                block("other:tin_ore", "c:ores", "c:ores/tin"),
                block("example:tin_ore", "c:ores", "c:ores/tin"),
                block("minecraft:iron_ore", "c:ores", "c:ores/iron"));

        var result = OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY);

        assertEquals(
                List.of("example:tin", "other:tin"),
                result.groups().stream().map(Group::id).toList());
        Group example = result.groups().getFirst();
        assertEquals(Evidence.CONVENTIONAL_TAG, example.evidence());
        assertFalse(example.reviewRequired());
        assertEquals(
                List.of("example:deepslate_tin_ore", "example:tin_ore"),
                example.candidates().stream().map(Candidate::blockId).toList());
        assertEquals(HostKind.DEEPSLATE, example.candidates().get(0).hostKind());
        assertEquals(
                "minecraft:deepslate_ore_replaceables",
                example.candidates().get(0).replaceTag());
        assertEquals(HostKind.STONE, example.candidates().get(1).hostKind());
        assertEquals(
                "minecraft:stone_ore_replaceables", example.candidates().get(1).replaceTag());
    }

    @Test
    void vanillaIsOptInAndNeverCrossGroupsProviders() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("minecraft:iron_ore", "c:ores/iron"), block("example:iron_ore", "c:ores/iron"));

        assertEquals(
                List.of("example:iron"),
                OreImportDiscovery.discover(registry, DiscoveryOptions.MODDED_ONLY).groups().stream()
                        .map(Group::id)
                        .toList());
        assertEquals(
                List.of("example:iron", "minecraft:iron"),
                OreImportDiscovery.discover(registry, DiscoveryOptions.INCLUDING_VANILLA).groups().stream()
                        .map(Group::id)
                        .toList());
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
        assertEquals("example:lead", lead.id());
        assertEquals(Evidence.ORE_LIKE_NAME, lead.evidence());
        assertEquals(
                List.of("example:lead_ore", "example:ore_lead"),
                lead.candidates().stream().map(Candidate::blockId).toList());
    }

    @Test
    void conventionalEvidenceWinsAndUnknownHostsRequireReview() {
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

        assertEquals("example:tin", group.id());
        assertTrue(group.reviewRequired());
        assertEquals(HostKind.STONE, tinOre.hostKind());
        assertEquals(HostKind.STONE, stoneTinOre.hostKind());
        assertEquals(HostKind.REVIEW_REQUIRED, netherTinOre.hostKind());
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
        Group tinCluster = requireNonNull(groups.get("example:tin_cluster"));
        Group lead = requireNonNull(groups.get("example:lead"));

        assertEquals(
                HostKind.REVIEW_REQUIRED, tinCluster.candidates().getFirst().hostKind());
        assertEquals(HostKind.STONE, lead.candidates().getFirst().hostKind());
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
    void oversizedFamiliesAreTruncatedExplicitlyAtTheModelBoundary() {
        List<OreImportRegistry.BlockEntry> entries = new ArrayList<>();
        for (int index = 0; index < OreImportModels.MAX_CANDIDATES_PER_GROUP + 1; index++) {
            entries.add(block("example:variant_" + index + "_ore", "c:ores/tin"));
        }

        var result = OreImportDiscovery.discover(new FakeOreImportRegistry(entries), DiscoveryOptions.MODDED_ONLY);

        assertTrue(result.truncated());
        assertEquals(
                OreImportModels.MAX_CANDIDATES_PER_GROUP,
                result.groups().getFirst().candidates().size());

        Candidate candidate = new Candidate("example:tin_ore", "", HostKind.STONE, Evidence.ORE_LIKE_NAME, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Group(
                        "example:tin",
                        "example",
                        "tin",
                        Evidence.ORE_LIKE_NAME,
                        java.util.Collections.nCopies(OreImportModels.MAX_CANDIDATES_PER_GROUP + 1, candidate),
                        false));
        Group bounded = new Group("example:tin", "example", "tin", Evidence.ORE_LIKE_NAME, List.of(candidate), false);
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
    }
}
