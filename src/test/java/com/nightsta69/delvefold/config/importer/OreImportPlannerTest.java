package com.nightsta69.delvefold.config.importer;

import static com.nightsta69.delvefold.config.importer.FakeOreImportRegistry.block;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.OreRuleTemplates;
import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiffEntry;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OreImportPlannerTest {
    @Test
    void createsExactOptionalTargetsWithTheEstablishedUncommonPreset() {
        OreProfileDocument base = OrePresets.empty();
        Group tin = group(
                "example",
                "tin",
                candidate("example:tin_ore", HostKind.STONE),
                candidate("example:deepslate_tin_ore", HostKind.DEEPSLATE));
        FakeOreImportRegistry registry =
                FakeOreImportRegistry.of(block("example:tin_ore"), block("example:deepslate_tin_ore"));

        var plan = OreImportPlanner.plan(base, List.of(tin), registry, RegistryLookup.SKIP);

        assertTrue(plan.valid(), () -> plan.validation().issues().toString());
        assertEquals(List.of(), base.rules(), "Planning must not mutate its base profile");
        assertEquals("empty", plan.baseProfileId());
        assertEquals(1, plan.proposedProfile().rules().size());
        OreRule imported = plan.proposedProfile().rules().getFirst();
        assertEquals("example_tin", imported.id());
        assertTrue(imported.enabled());
        assertFalse(imported.required());
        assertEquals(EnumSet.allOf(TerrainMode.class), imported.terrainModes());
        assertEquals(BiomeFilter.ALL_MINING_BIOMES, imported.biomes());
        assertEquals(List.of(OreRuleTemplates.uncommonBand()), imported.bands());
        assertEquals(
                List.of("example:deepslate_tin_ore", "example:tin_ore"),
                imported.targets().stream().map(OreTarget::block).toList());
        assertEquals(
                List.of("minecraft:deepslate_ore_replaceables", "minecraft:stone_ore_replaceables"),
                imported.targets().stream().map(OreTarget::replaceTag).toList());
        assertTrue(imported.targets().stream().allMatch(target -> target.weight() == OreTarget.DEFAULT_WEIGHT));
        for (TerrainMode terrain : TerrainMode.values()) {
            assertEquals(0.0D, plan.beforeWorkload().forTerrain(terrain).attemptsPerChunk());
            assertEquals(8.0D, plan.afterWorkload().forTerrain(terrain).attemptsPerChunk());
            assertEquals(64.0D, plan.afterWorkload().forTerrain(terrain).workUnits());
        }
    }

    @Test
    void exactAndExpandedTagCoverageSkipOnlyCoveredMembers() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:tin_ore"),
                block("example:deepslate_tin_ore"),
                block("example:copper_ore", "c:ores/copper"),
                block("example:deepslate_copper_ore", "c:ores/copper"));
        OreRule exact = rule("existing_tin", List.of(OreTarget.of("example:tin_ore", HostKind.STONE.replaceTag())));
        OreRule tagged =
                rule("existing_copper", List.of(OreTarget.ofTag("c:ores/copper", HostKind.STONE.replaceTag())));
        OreProfileDocument base = new OreProfileDocument(2, 4L, "base", List.of(exact, tagged));
        Group tin = group(
                "example",
                "tin",
                candidate("example:tin_ore", HostKind.STONE),
                candidate("example:deepslate_tin_ore", HostKind.DEEPSLATE));
        Group copper = group(
                "example",
                "copper",
                candidate("example:copper_ore", HostKind.STONE),
                candidate("example:deepslate_copper_ore", HostKind.DEEPSLATE));

        var plan = OreImportPlanner.plan(base, List.of(tin, copper), registry, RegistryLookup.SKIP);
        Map<String, DiffEntry> diff =
                plan.diff().stream().collect(Collectors.toMap(DiffEntry::groupId, Function.identity()));
        DiffEntry copperDiff = requireNonNull(diff.get("example:copper"));
        DiffEntry tinDiff = requireNonNull(diff.get("example:tin"));

        assertEquals(3, plan.proposedProfile().rules().size());
        assertEquals(DiffStatus.SKIPPED_COVERED, copperDiff.status());
        assertEquals("message.delvefold.import.diff_message.covered", translationKey(copperDiff.message()));
        assertEquals(List.of("example:copper_ore", "example:deepslate_copper_ore"), copperDiff.skippedBlocks());
        assertEquals(DiffStatus.PARTIALLY_ADDED, tinDiff.status());
        assertEquals("message.delvefold.import.diff_message.partially_added", translationKey(tinDiff.message()));
        assertEquals(List.of("example:deepslate_tin_ore"), tinDiff.addedBlocks());
        assertEquals(List.of("example:tin_ore"), tinDiff.skippedBlocks());
        OreRule imported = plan.proposedProfile().rules().getLast();
        assertEquals(
                List.of("example:deepslate_tin_ore"),
                imported.targets().stream().map(OreTarget::block).toList());
    }

    @Test
    void reviewRequiredCandidatesNeverReceiveAGuessedHost() {
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(
                block("example:tin_ore"), block("example:nether_tin_ore"), block("example:lead_cluster"));
        Group mixed = group(
                "example",
                "tin",
                candidate("example:tin_ore", HostKind.STONE),
                candidate("example:nether_tin_ore", HostKind.REVIEW_REQUIRED));
        Group reviewOnly = group("example", "lead", candidate("example:lead_cluster", HostKind.REVIEW_REQUIRED));

        var plan = OreImportPlanner.plan(OrePresets.empty(), List.of(reviewOnly, mixed), registry, RegistryLookup.SKIP);
        Map<String, DiffEntry> diff =
                plan.diff().stream().collect(Collectors.toMap(DiffEntry::groupId, Function.identity()));
        DiffEntry leadDiff = requireNonNull(diff.get("example:lead"));
        DiffEntry tinDiff = requireNonNull(diff.get("example:tin"));

        assertEquals(1, plan.proposedProfile().rules().size());
        assertEquals(DiffStatus.SKIPPED_REVIEW_REQUIRED, leadDiff.status());
        assertEquals("message.delvefold.import.diff_message.review_required", translationKey(leadDiff.message()));
        assertEquals(DiffStatus.PARTIALLY_ADDED, tinDiff.status());
        assertEquals(List.of("example:tin_ore"), tinDiff.addedBlocks());
        assertEquals(List.of("example:nether_tin_ore"), tinDiff.skippedBlocks());
    }

    private static String translationKey(String message) {
        return AdminLocalizedMessage.decode(message).orElseThrow().translationKey();
    }

    @Test
    void ruleIdsAndOutputOrderAreDeterministicWithoutOverwritingCollisions() {
        OreRule collision =
                rule("example_tin", List.of(OreTarget.of("minecraft:coal_ore", HostKind.STONE.replaceTag())));
        OreProfileDocument base = new OreProfileDocument(2, 0L, "base", List.of(collision));
        Group tin = group("example", "tin", candidate("example:tin_ore", HostKind.STONE));
        Group copper = group("alpha", "copper", candidate("alpha:copper_ore", HostKind.STONE));
        FakeOreImportRegistry registry = FakeOreImportRegistry.of(block("example:tin_ore"), block("alpha:copper_ore"));

        var forward = OreImportPlanner.plan(base, List.of(tin, copper), registry, RegistryLookup.SKIP);
        var reverse = OreImportPlanner.plan(base, List.of(copper, tin), registry, RegistryLookup.SKIP);

        assertEquals(forward, reverse);
        assertEquals(
                List.of("example_tin", "alpha_copper", "example_tin_2"),
                forward.proposedProfile().rules().stream().map(OreRule::id).toList());
        assertEquals(
                2,
                OreImportPlanner.plan(base, List.of(tin, tin), registry, RegistryLookup.SKIP)
                        .proposedProfile()
                        .rules()
                        .size());

        Group conflicting =
                groupWithId("example:tin", "example", "different", candidate("example:tin_ore", HostKind.STONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreImportPlanner.plan(base, List.of(tin, conflicting), registry, RegistryLookup.SKIP));
    }

    @Test
    void previewReportsCanonicalWorkloadAndValidatorBudgetFailures() {
        List<OreRule> baseRules = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            baseRules.add(new OreRule(
                    "stress_" + index,
                    true,
                    false,
                    EnumSet.allOf(TerrainMode.class),
                    List.of(OreTarget.of("minecraft:stone", HostKind.STONE.replaceTag())),
                    BiomeFilter.ALL_MINING_BIOMES,
                    List.of(SpawnBand.uniform("main", 1, 256.0D, -64, 64, 0.0D))));
        }
        OreProfileDocument base = new OreProfileDocument(2, 0L, "stress", baseRules);
        Group tin = group("example", "tin", candidate("example:tin_ore", HostKind.STONE));

        var plan = OreImportPlanner.plan(
                base, List.of(tin), FakeOreImportRegistry.of(block("example:tin_ore")), RegistryLookup.SKIP);

        assertFalse(plan.valid());
        assertTrue(
                plan.validation().issues().stream().anyMatch(issue -> "budget.too_many_attempts".equals(issue.code())));
        for (TerrainMode terrain : TerrainMode.values()) {
            assertEquals(4096.0D, plan.beforeWorkload().forTerrain(terrain).attemptsPerChunk());
            assertEquals(4104.0D, plan.afterWorkload().forTerrain(terrain).attemptsPerChunk());
            assertEquals(4160.0D, plan.afterWorkload().forTerrain(terrain).workUnits());
        }
    }

    @Test
    void selectionCountIsBoundedBeforePlanning() {
        Group tin = group("example", "tin", candidate("example:tin_ore", HostKind.STONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreImportPlanner.plan(
                        OrePresets.empty(),
                        java.util.Collections.nCopies(OreImportModels.MAX_SELECTED_GROUPS + 1, tin),
                        FakeOreImportRegistry.of(block("example:tin_ore")),
                        RegistryLookup.SKIP));
    }

    private static Candidate candidate(String blockId, HostKind host) {
        return new Candidate(blockId, host.replaceTag(), host, Evidence.CONVENTIONAL_TAG, List.of());
    }

    private static Group group(String namespace, String material, Candidate... candidates) {
        return groupWithId(namespace + ':' + material, namespace, material, candidates);
    }

    private static Group groupWithId(String id, String namespace, String material, Candidate... candidates) {
        return new Group(id, namespace, material, Evidence.CONVENTIONAL_TAG, List.of(candidates), false);
    }

    private static OreRule rule(String id, List<OreTarget> targets) {
        return new OreRule(
                id,
                true,
                false,
                EnumSet.allOf(TerrainMode.class),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(OreRuleTemplates.uncommonBand()));
    }
}
