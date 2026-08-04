package com.nightsta69.delvefold.client.gui;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreFamilyCatalogTest {
    @Test
    void serverFamilySummaryExpandsIntoCrossProviderHostVariants() {
        Group local = new Group(
                "delvefold:ores/copper",
                "delvefold",
                "copper",
                Evidence.CONVENTIONAL_TAG,
                List.of(
                        candidate("minecraft:copper_ore", HostKind.STONE),
                        candidate("minecraft:deepslate_copper_ore", HostKind.DEEPSLATE),
                        candidate("example:copper_ore", HostKind.STONE)),
                false);
        OreLibraryView.Family summary = new OreLibraryView.Family(
                "delvefold:ores/copper",
                "copper",
                "delvefold_copper_2",
                "minecraft:copper_ore",
                HostKind.STONE,
                2,
                3,
                3,
                Evidence.CONVENTIONAL_TAG,
                false,
                false,
                false);

        OreLibraryPickerState.Family family = OreFamilyCatalog.present(summary, local);

        assertEquals("Copper", family.displayName());
        assertEquals("delvefold_copper_2", family.ruleId());
        assertEquals(List.of("minecraft", "example"), family.providerNamespaces());
        assertEquals(
                List.of("stone", "deepslate", "stone"),
                family.candidates().stream()
                        .map(OreLibraryPickerState.Candidate::hostVariant)
                        .toList());
        assertFalse(family.reviewRequired());
        assertEquals("minecraft", OreFamilyCatalog.preferredProvider(summary));
    }

    @Test
    void missingClientDetailRetainsServerRepresentativeAndReviewState() {
        OreLibraryView.Family summary = new OreLibraryView.Family(
                "delvefold:ores/oddium",
                "oddium",
                "delvefold_oddium",
                "example:nether_oddium_ore",
                HostKind.REVIEW_REQUIRED,
                1,
                1,
                0,
                Evidence.ORE_LIKE_NAME,
                false,
                true,
                false);

        OreLibraryPickerState.Family family = OreFamilyCatalog.present(summary, null);

        assertEquals(1, family.candidates().size());
        assertTrue(family.candidates().getFirst().reviewRequired());
        assertEquals("", family.candidates().getFirst().replaceTag());
    }

    @Test
    void missingClientDetailRetainsAuthoritativeNetherAndEndHosts() {
        for (HostKind hostKind : List.of(HostKind.NETHERRACK, HostKind.END_STONE)) {
            OreLibraryView.Family summary = new OreLibraryView.Family(
                    "delvefold:ores/garnet",
                    "garnet",
                    "delvefold_garnet",
                    hostKind == HostKind.NETHERRACK ? "silentgems:nether_garnet_ore" : "silentgems:end_garnet_ore",
                    hostKind,
                    1,
                    1,
                    1,
                    Evidence.CONVENTIONAL_TAG,
                    false,
                    false,
                    false);

            OreLibraryPickerState.Candidate candidate =
                    OreFamilyCatalog.present(summary, null).candidates().getFirst();

            assertEquals(hostKind == HostKind.NETHERRACK ? "nether" : "end", candidate.hostVariant());
            assertEquals(hostKind.replaceTag(), candidate.replaceTag());
            assertFalse(candidate.reviewRequired());
        }
    }

    @Test
    void reviewCandidatesRetainBlankHostsForExplicitWizardSelection() {
        Group local = new Group(
                "delvefold:ores/oddium",
                "delvefold",
                "oddium",
                Evidence.CONVENTIONAL_TAG,
                List.of(candidate("example:nether_oddium_ore", HostKind.REVIEW_REQUIRED)),
                true);

        OreLibraryPickerState.Candidate candidate =
                OreFamilyCatalog.candidatesForWizard(local).getFirst();

        assertTrue(candidate.reviewRequired());
        assertEquals("review_required", candidate.hostVariant());
        assertEquals("", candidate.replaceTag());
    }

    @Test
    void explicitNetherAndEndHostsReachTheWizardAsEditableSafeVariants() {
        Group local = new Group(
                "delvefold:ores/garnet",
                "delvefold",
                "garnet",
                Evidence.CONVENTIONAL_TAG,
                List.of(
                        candidate("silentgems:garnet_ore", HostKind.STONE),
                        candidate("silentgems:deepslate_garnet_ore", HostKind.DEEPSLATE),
                        candidate("silentgems:nether_garnet_ore", HostKind.NETHERRACK),
                        candidate("silentgems:end_garnet_ore", HostKind.END_STONE)),
                false);

        List<OreLibraryPickerState.Candidate> candidates = OreFamilyCatalog.candidatesForWizard(local);
        var byBlock = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OreLibraryPickerState.Candidate::blockId, candidate -> candidate));
        var stone = requireNonNull(byBlock.get("silentgems:garnet_ore"));
        var deepslate = requireNonNull(byBlock.get("silentgems:deepslate_garnet_ore"));
        var nether = requireNonNull(byBlock.get("silentgems:nether_garnet_ore"));
        var end = requireNonNull(byBlock.get("silentgems:end_garnet_ore"));

        assertEquals("stone", stone.hostVariant());
        assertEquals("minecraft:stone_ore_replaceables", stone.replaceTag());
        assertEquals("deepslate", deepslate.hostVariant());
        assertEquals("minecraft:deepslate_ore_replaceables", deepslate.replaceTag());
        assertEquals("nether", nether.hostVariant());
        assertEquals("c:netherracks", nether.replaceTag());
        assertEquals("end", end.hostVariant());
        assertEquals("c:end_stones", end.replaceTag());
        assertTrue(candidates.stream().noneMatch(OreLibraryPickerState.Candidate::reviewRequired));
    }

    private static Candidate candidate(String blockId, HostKind hostKind) {
        return new Candidate(
                blockId, hostKind.replaceTag(), hostKind, Evidence.CONVENTIONAL_TAG, List.of("c:ores/copper"));
    }
}
