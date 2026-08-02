package com.nightsta69.delvefold.client.gui;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreLibraryPickerStateTest {
    @Test
    void vanillaProviderIsTheSafeDefaultAndProviderChoicesRemainEditable() {
        OreLibraryPickerState state = state(copper(false), tin(false));

        assertTrue(state.candidateSelected("delvefold:ores/copper", "minecraft:copper_ore"));
        assertTrue(state.candidateSelected("delvefold:ores/copper", "minecraft:deepslate_copper_ore"));
        assertFalse(state.candidateSelected("delvefold:ores/copper", "example:copper_ore"));

        assertTrue(state.selectProvider("delvefold:ores/copper", "example"));
        assertFalse(state.candidateSelected("delvefold:ores/copper", "minecraft:copper_ore"));
        assertTrue(state.candidateSelected("delvefold:ores/copper", "example:copper_ore"));
    }

    @Test
    void reviewOnlyLexicalProviderDoesNotHideALaterSafeProvider() {
        OreLibraryPickerState.Family family = new OreLibraryPickerState.Family(
                "delvefold:ores/silver",
                "silver",
                "Silver",
                "delvefold_silver",
                false,
                true,
                List.of(
                        new OreLibraryPickerState.Candidate(
                                "aardvark:nether_silver_ore", "aardvark", "review_required", "", true),
                        candidate("alpha:silver_ore", "alpha", "stone")));

        OreLibraryPickerState state = state(family);

        assertFalse(state.candidateSelected("delvefold:ores/silver", "aardvark:nether_silver_ore"));
        assertTrue(state.candidateSelected("delvefold:ores/silver", "alpha:silver_ore"));
        assertTrue(state.toggleFamily("delvefold:ores/silver"));
    }

    @Test
    void reviewOnlyFamilyCanOpenAnExplicitHostDraftButCannotEnterTheSafeBatch() {
        OreLibraryPickerState.Family family = new OreLibraryPickerState.Family(
                "delvefold:ores/oddium",
                "oddium",
                "Oddium",
                "delvefold_oddium",
                false,
                true,
                List.of(new OreLibraryPickerState.Candidate(
                        "example:nether_oddium_ore", "example", "review_required", "", true)));
        OreLibraryPickerState state = state(family);

        assertFalse(state.toggleFamily(family.id()));
        assertTrue(state.selectedDrafts().isEmpty());
        AdminSnapshot.OreRuleDraft manualDraft = requireNonNull(state.draft(family.id()));
        assertEquals(
                "example:nether_oddium_ore", manualDraft.variants().getFirst().sourceId());
    }

    @Test
    void configuredFamiliesAreHiddenByDefaultButCanBeReviewed() {
        OreLibraryPickerState state = state(copper(true), tin(false));

        assertEquals(
                List.of("tin"),
                state.filteredFamilies().stream()
                        .map(OreLibraryPickerState.Family::material)
                        .toList());
        state.toggleShowConfigured();
        assertEquals(
                List.of("copper", "tin"),
                state.filteredFamilies().stream()
                        .map(OreLibraryPickerState.Family::material)
                        .toList());

        state.setSearchQuery("minecraft");
        assertEquals(
                List.of("copper"),
                state.filteredFamilies().stream()
                        .map(OreLibraryPickerState.Family::material)
                        .toList());
    }

    @Test
    void selectVisibleClearAndCandidateTogglesBuildOneDraftPerFamily() {
        OreLibraryPickerState state = state(copper(false), tin(false));
        assertEquals(2, state.selectVisible(state.filteredFamilies()));
        assertTrue(state.toggleCandidate("delvefold:ores/copper", "example:copper_ore"));

        List<AdminSnapshot.OreRuleDraft> drafts = state.selectedDrafts();
        assertEquals(
                List.of("delvefold_copper", "delvefold_tin"),
                drafts.stream().map(AdminSnapshot.OreRuleDraft::id).toList());
        AdminSnapshot.OreRuleDraft copper = drafts.getFirst();
        assertEquals("minecraft:copper_ore", copper.primaryBlockId());
        assertEquals(
                List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore", "example:copper_ore"),
                copper.variants().stream()
                        .map(AdminSnapshot.OreVariantDraft::blockId)
                        .toList());

        state.clearSelection();
        assertEquals(0, state.selectedCount());
        assertTrue(state.selectedDrafts().isEmpty());
    }

    @Test
    void refreshedCatalogRetainsOnlyChoicesStillPresent() {
        OreLibraryPickerState state = state(copper(false));
        state.selectProvider("delvefold:ores/copper", "example");
        state.toggleFamily("delvefold:ores/copper");

        OreLibraryPickerState.Family replacement = new OreLibraryPickerState.Family(
                "delvefold:ores/copper",
                "copper",
                "Copper",
                "delvefold_copper",
                false,
                false,
                List.of(candidate("other:copper_ore", "other", "stone")));
        state.replaceFamilies(List.of(replacement));

        assertTrue(state.selected("delvefold:ores/copper"));
        assertTrue(state.candidateSelected("delvefold:ores/copper", "other:copper_ore"));
    }

    @Test
    void serverPageChangesRetainBatchSelectionUntilTheCatalogTokenChanges() {
        OreLibraryPickerState state = new OreLibraryPickerState();
        state.acceptPage("catalog-a", List.of(copper(false)));
        assertTrue(state.toggleFamily("delvefold:ores/copper"));

        state.acceptPage("catalog-a", List.of(tin(false)));
        assertEquals(List.of("delvefold:ores/copper"), state.selectedFamilyIds());
        assertEquals(
                List.of("tin"),
                state.pageFamilies().stream()
                        .map(OreLibraryPickerState.Family::material)
                        .toList());

        state.acceptPage("catalog-b", List.of(tin(false)));
        assertTrue(state.selectedFamilyIds().isEmpty());
    }

    @Test
    void expiredCatalogRefreshRetainsCompatibleCrossPageSelections() {
        OreLibraryPickerState state = new OreLibraryPickerState();
        state.acceptPage("catalog-a", List.of(copper(false)));
        assertTrue(state.toggleFamily("delvefold:ores/copper"));
        state.acceptPage("catalog-a", List.of(tin(false)));

        state.acceptPage("catalog-b", List.of(tin(false)), true);

        assertEquals(List.of("delvefold:ores/copper"), state.selectedFamilyIds());
        assertTrue(state.candidateSelected("delvefold:ores/copper", "minecraft:copper_ore"));
    }

    @Test
    void finalLocalWindowUsesTheDiscretePageBoundary() {
        assertEquals(18, OreLibraryPickerState.lastLocalWindowOffset(20, 9));
        assertEquals(9, OreLibraryPickerState.lastLocalWindowOffset(18, 9));
        assertEquals(0, OreLibraryPickerState.lastLocalWindowOffset(9, 9));
        assertEquals(0, OreLibraryPickerState.lastLocalWindowOffset(0, 9));
    }

    @Test
    void refreshedLocalWindowRetainsContextWithinTheReplacementPageBounds() {
        assertEquals(9, OreLibraryPickerState.clampLocalWindowOffset(9, 20, 9));
        assertEquals(18, OreLibraryPickerState.clampLocalWindowOffset(99, 20, 9));
        assertEquals(0, OreLibraryPickerState.clampLocalWindowOffset(9, 7, 9));
        assertEquals(7, OreLibraryPickerState.clampLocalWindowOffset(9, 20, 7));
    }

    @Test
    void staleResponsesAreRecognizedAgainstTheLatestNormalizedFilters() {
        assertTrue(OreLibraryPickerState.responseMatchesFilters(" Copper ", true, "copper", true));
        assertFalse(OreLibraryPickerState.responseMatchesFilters("tin", true, "copper", true));
        assertFalse(OreLibraryPickerState.responseMatchesFilters("copper", false, "copper", true));
    }

    @Test
    void deferredServerPagesUseTheServersClampingRules() {
        assertEquals(0, OreLibraryPickerState.clampServerPage(-1, 3));
        assertEquals(1, OreLibraryPickerState.clampServerPage(1, 3));
        assertEquals(2, OreLibraryPickerState.clampServerPage(99, 3));
        assertEquals(0, OreLibraryPickerState.clampServerPage(2, 0));
    }

    @Test
    void invalidatedCatalogSessionsChooseSafeAutomaticRecoveryBehavior() {
        assertEquals(
                OreLibraryPickerState.CatalogRecovery.RETAIN_SELECTIONS,
                OreLibraryPickerState.catalogRecovery(
                        AdminLocalizedMessage.encode("message.delvefold.import.session.expired")));
        assertEquals(
                OreLibraryPickerState.CatalogRecovery.RESET_SELECTIONS,
                OreLibraryPickerState.catalogRecovery(
                        AdminLocalizedMessage.encode("message.delvefold.import.session.registry_changed")));
        assertEquals(
                OreLibraryPickerState.CatalogRecovery.RESET_SELECTIONS,
                OreLibraryPickerState.catalogRecovery(
                        AdminLocalizedMessage.encode("message.delvefold.import.session.base_changed")));

        assertEquals(
                OreLibraryPickerState.CatalogRecovery.NONE,
                OreLibraryPickerState.catalogRecovery(
                        AdminLocalizedMessage.encode("message.delvefold.import.rate_limited")));
        assertEquals(
                OreLibraryPickerState.CatalogRecovery.NONE,
                OreLibraryPickerState.catalogRecovery(
                        AdminLocalizedMessage.encode("message.delvefold.ore_library.internal_error")));
        assertEquals(
                OreLibraryPickerState.CatalogRecovery.NONE,
                OreLibraryPickerState.catalogRecovery("ordinary server text"));
    }

    private static OreLibraryPickerState state(OreLibraryPickerState.Family... families) {
        OreLibraryPickerState state = new OreLibraryPickerState();
        state.replaceFamilies(List.of(families));
        return state;
    }

    private static OreLibraryPickerState.Family copper(boolean configured) {
        return new OreLibraryPickerState.Family(
                "delvefold:ores/copper",
                "copper",
                "Copper",
                "delvefold_copper",
                configured,
                false,
                List.of(
                        candidate("minecraft:copper_ore", "minecraft", "stone"),
                        candidate("minecraft:deepslate_copper_ore", "minecraft", "deepslate"),
                        candidate("example:copper_ore", "example", "stone")));
    }

    private static OreLibraryPickerState.Family tin(boolean configured) {
        return new OreLibraryPickerState.Family(
                "delvefold:ores/tin",
                "tin",
                "Tin",
                "delvefold_tin",
                configured,
                true,
                List.of(candidate("example:tin_ore", "example", "stone")));
    }

    private static OreLibraryPickerState.Candidate candidate(String id, String provider, String host) {
        return new OreLibraryPickerState.Candidate(
                id,
                provider,
                host,
                host.equals("deepslate") ? "minecraft:deepslate_ore_replaceables" : "minecraft:stone_ore_replaceables",
                false);
    }
}
