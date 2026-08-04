package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the server-paged, atomic, return-preserving Unified Ores GUI integration. */
class OreLibraryPickerSourceContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void pickerUsesAuthoritativePagingAndOneAtomicBatchRequest() throws Exception {
        String picker = Files.readString(ROOT.resolve("client/gui/DelvefoldOrePickerScreen.java"));

        assertTrue(picker.contains("public void acceptLibrary(OreLibraryView replacement)"));
        assertTrue(picker.contains("new OreLibraryRequestPayload("));
        assertTrue(picker.contains("new AddOreFamiliesPayload("));
        assertTrue(picker.contains("this.state.selectVisible(visiblePageFamilies())"));
        assertTrue(picker.contains("this.state.clearSelection()"));
        assertTrue(picker.contains("screen.delvefold.ore_picker.show_configured"));
        assertTrue(picker.contains("screen.delvefold.ore_picker.exact_id"));
        assertTrue(picker.contains("OreRuleWizardDraftState.inferredHost(id.toString())"));
        assertTrue(picker.contains("requestLibrary(active.page() - 1, true)"));
        assertTrue(picker.contains("OreLibraryPickerState.lastLocalWindowOffset(presented.size(), this.visibleRows)"));
        assertFalse(picker.contains("new SaveOreRulePayload"));
    }

    @Test
    void acceptedSavesReturnToPickerAndExistingRulesDiscoverCrossProviderVariants() throws Exception {
        String handler = Files.readString(ROOT.resolve("client/DelvefoldClientPayloadHandler.java"));
        String wizard = Files.readString(ROOT.resolve("client/gui/DelvefoldOreRuleWizardScreen.java"));
        String catalog = Files.readString(ROOT.resolve("client/gui/OreFamilyCatalog.java"));

        assertTrue(handler.contains("picker.acceptLibrary(payload.view())"));
        assertTrue(handler.contains("wizard.acceptedReturnScreen(payload.snapshot())"));
        assertTrue(wizard.contains("this.parent instanceof DelvefoldOrePickerScreen picker"));
        assertTrue(wizard.contains("OreFamilyCatalog.discoverInstalled()"));
        assertTrue(wizard.contains("MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY"));
        assertTrue(catalog.contains("summary.suggestedRuleId()"));
        assertFalse(catalog.contains("private static String ruleId("));

        int standardConstructor = wizard.indexOf("public DelvefoldOreRuleWizardScreen(");
        int familyConstructor = wizard.indexOf("List<OreLibraryPickerState.Candidate> candidateVariants) {");
        int privateConstructor = wizard.indexOf("private DelvefoldOreRuleWizardScreen(", familyConstructor);
        assertTrue(standardConstructor >= 0
                && familyConstructor > standardConstructor
                && privateConstructor > familyConstructor);
        assertTrue(wizard.substring(standardConstructor, familyConstructor)
                .contains("snapshot.oreRules().stream().anyMatch"));
        assertTrue(wizard.substring(familyConstructor, privateConstructor)
                .contains("\n                false,\n                draft.id(),"));
        assertTrue(wizard.contains("this.draftState.selectedVariants.put(blockId, candidate.replaceTag())"));
        assertTrue(wizard.contains("initialFamilyReplacementTags(draft, candidateVariants)"));
        assertTrue(wizard.contains("this.draftState.selectedVariants,"));
        assertTrue(wizard.contains("result.put(candidate.blockId(), \"\")"));
        assertTrue(wizard.contains("screen.delvefold.status.review_required"));
        assertTrue(wizard.contains("screen.delvefold.ore_wizard.variant.review_tooltip"));
        assertTrue(wizard.contains("screen.delvefold.ore_wizard.variant.detected_tooltip"));
        assertTrue(wizard.contains("case \"c:netherracks\" -> \"nether\""));
        assertTrue(wizard.contains("case \"c:end_stones\" -> \"end\""));
        assertTrue(wizard.contains("screen.delvefold.ore_wizard.validation.replacement_tag_required"));
        assertFalse(wizard.contains(
                "this.draftState.selectedVariants.put(blockId, OreRuleWizardDraftState.inferredHost(blockId))"));
    }

    @Test
    void familyStatusSeparatesUnusableFamiliesFromIndividualReviewVariants() throws Exception {
        String picker = Files.readString(ROOT.resolve("client/gui/DelvefoldOrePickerScreen.java"));

        assertTrue(picker.contains("private static Component familyReviewStatus(OreLibraryView.Family family)"));
        assertTrue(picker.contains("family.importableCandidateCount() < family.candidateCount()"));
        assertTrue(picker.contains("screen.delvefold.status.variants_need_review"));
    }

    @Test
    void pickerQueuesLatestInFlightFiltersAndRestoresItsLocalWindowAfterSave() throws Exception {
        String picker = Files.readString(ROOT.resolve("client/gui/DelvefoldOrePickerScreen.java"));

        assertTrue(picker.contains("this.queuedPage = Math.max(0, page)"));
        assertTrue(picker.contains("OreLibraryPickerState.responseMatchesFilters("));
        assertTrue(picker.contains("requestLibrary(deferredPage, deferredEnterAtEnd)"));
        assertTrue(picker.contains("refreshed.requestedInitialLocalOffset = this.localOffset"));
        assertTrue(picker.contains("OreLibraryPickerState.clampLocalWindowOffset("));
    }

    @Test
    void pagerIgnoresInFlightClicksAndInvalidCatalogsRestartOnlyOnce() throws Exception {
        String picker = Files.readString(ROOT.resolve("client/gui/DelvefoldOrePickerScreen.java"));

        assertTrue(picker.contains("private void previousPage() {\n        if (this.loading)"));
        assertTrue(picker.contains("private void nextPage() {\n        if (this.loading)"));
        assertTrue(picker.contains("this.catalogRequestPending"));
        assertTrue(picker.contains("!this.catalogRecoveryAttempted"));
        assertTrue(picker.contains("OreLibraryPickerState.catalogRecovery(payload.message())"));
        assertTrue(picker.contains("restartCatalog(recovery.retainSelections())"));
        assertTrue(picker.contains("this.retainSelectionsAcrossCatalogRefresh = retainSelections;"));
        assertTrue(picker.contains("this.library = null;"));
        assertTrue(picker.contains("this.localGroups = Map.of();"));
        assertTrue(picker.contains("this.catalogRecoveryAttempted = true;"));
        assertTrue(picker.contains("this.catalogRecoveryAttempted = false;"));
    }
}
