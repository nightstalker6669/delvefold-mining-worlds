package com.nightsta69.delvefold.client.gui;

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

    private static Candidate candidate(String blockId, HostKind hostKind) {
        return new Candidate(
                blockId, hostKind.replaceTag(), hostKind, Evidence.CONVENTIONAL_TAG, List.of("c:ores/copper"));
    }
}
