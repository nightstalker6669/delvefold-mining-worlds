package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.OreImportViews.CandidateView;
import com.nightsta69.delvefold.network.model.OreImportViews.DiffView;
import com.nightsta69.delvefold.network.model.OreImportViews.GroupView;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Characterizes local ore-import interaction state independently of Minecraft widgets and network sends. */
class OreImportScreenStateTest {
    @Test
    void scanNavigationConsumesLocalWindowsBeforeRequestingAnotherServerPage() {
        OreImportScreenState state = state();
        state.acceptScan(scan("scan-a", 0, 8));
        assertEquals(new OreImportScreenState.PageWindow(0, 2, 2), state.resizeScanWindow(56));

        OreImportScreenState.PageTransition firstNext = state.nextScanPage();
        int offsetAfterFirstNext = state.localOffset();
        OreImportScreenState.PageTransition secondNext = state.nextScanPage();
        OreImportScreenState.PageTransition previousLocal = state.previousScanPage();
        int offsetAfterPrevious = state.localOffset();

        assertAll(
                () -> assertTrue(firstNext.localChanged()),
                () -> assertFalse(firstNext.requestsServerPage()),
                () -> assertEquals(2, offsetAfterFirstNext),
                () -> assertFalse(secondNext.localChanged()),
                () -> assertTrue(secondNext.requestsServerPage()),
                () -> assertEquals(1, secondNext.requestedPage()),
                () -> assertTrue(previousLocal.localChanged()),
                () -> assertEquals(0, offsetAfterPrevious));

        state.acceptScan(scan("scan-a", 1, 8));
        state.resizeScanWindow(56);
        OreImportScreenState.PageTransition previousServerPage = state.previousScanPage();
        assertAll(
                () -> assertFalse(previousServerPage.localChanged()),
                () -> assertTrue(previousServerPage.requestsServerPage()),
                () -> assertEquals(0, previousServerPage.requestedPage()));
    }

    @Test
    void previewNavigationUsesItsOwnRowHeightAndCommitPageBoundaries() {
        OreImportScreenState state = state();
        state.acceptScan(scan("scan-a", 0, 4));
        state.acceptPreview(preview("commit-a", 0, 4, true, 2));
        assertEquals(new OreImportScreenState.PageWindow(0, 1, 1), state.resizePreviewWindow(24));

        OreImportScreenState.PageTransition firstNext = state.nextPreviewPage();
        OreImportScreenState.PageTransition secondNext = state.nextPreviewPage();

        assertAll(
                () -> assertTrue(firstNext.localChanged()),
                () -> assertEquals(1, state.localOffset()),
                () -> assertTrue(secondNext.requestsServerPage()),
                () -> assertEquals(1, secondNext.requestedPage()));

        state.acceptPreview(preview("commit-a", 1, 4, true, 2));
        state.resizePreviewWindow(48);
        OreImportScreenState.PageTransition previousServerPage = state.previousPreviewPage();
        assertAll(
                () -> assertTrue(previousServerPage.requestsServerPage()),
                () -> assertEquals(0, previousServerPage.requestedPage()));
    }

    @Test
    void heightConstrainedPreviewCanExposeNoRowsAndStillCrossServerPages() {
        OreImportScreenState state = state();
        state.acceptPreview(preview("commit-a", 0, 40, true, 2));

        OreImportScreenState.PageWindow window = state.resizePreviewWindowAllowEmpty(15);
        OreImportScreenState.PageTransition next = state.moveRows(1);

        assertAll(
                () -> assertEquals(new OreImportScreenState.PageWindow(0, 0, 0), window),
                () -> assertEquals(0, state.visibleRows()),
                () -> assertTrue(next.requestsServerPage()),
                () -> assertEquals(1, next.requestedPage()));
    }

    @Test
    void scanAndPreviewReplacementPreserveTheEstablishedStaleStateSemantics() {
        OreImportScreenState state = state();
        ScanView firstScan = scan("scan-a", 0, 8);
        state.acceptScan(firstScan);
        String selectedId = firstScan.groups().getFirst().id();
        assertTrue(state.toggleGroup(selectedId, true));
        state.setTargetProfileId(" retained_profile ");

        ScanView sameSessionPage = scan("scan-a", 1, 8);
        state.acceptScan(sameSessionPage);
        assertAll(
                () -> assertSame(sameSessionPage, state.scan()),
                () -> assertTrue(state.selected(selectedId)),
                () -> assertNull(state.preview()),
                () -> assertEquals(0, state.localOffset()));

        PreviewView firstPreview = preview("commit-a", 0, 2, true, 1);
        PreviewView replacementPreview = preview("commit-b", 0, 2, true, 1);
        state.acceptPreview(firstPreview);
        state.acceptPreview(replacementPreview);
        assertAll(
                () -> assertSame(replacementPreview, state.preview()),
                () -> assertSame(sameSessionPage, state.scan()),
                () -> assertTrue(state.selected(selectedId)),
                () -> assertEquals("retained_profile", state.targetProfileId()));

        ScanView newSession = scan("scan-b", 0, 4);
        state.acceptScan(newSession);
        assertAll(
                () -> assertSame(newSession, state.scan()),
                () -> assertNull(state.preview()),
                () -> assertEquals(0, state.selectedCount()),
                () -> assertEquals("retained_profile", state.targetProfileId()));

        state.acceptPreview(firstPreview);
        assertSame(
                firstPreview,
                state.preview(),
                "The screen historically displays every client-thread preview replacement; authority is rechecked "
                        + "when its commit token is submitted");
        state.backToScan();
        assertAll(() -> assertNull(state.preview()), () -> assertSame(newSession, state.scan()));
    }

    @Test
    void selectionIsDeterministicAndUnimportableGroupsCannotChangeIt() {
        OreImportScreenState state = state();
        state.acceptScan(scan("scan-a", 0, 4));

        assertFalse(state.toggleGroup("example:review", false));
        assertTrue(state.toggleGroup("example:zinc", true));
        assertTrue(state.toggleGroup("example:aluminum", true));
        assertEquals(List.of("example:aluminum", "example:zinc"), state.sortedSelectedGroupIds());

        assertTrue(state.toggleGroup("example:zinc", true));
        assertAll(
                () -> assertFalse(state.selected("example:zinc")),
                () -> assertEquals(List.of("example:aluminum"), state.sortedSelectedGroupIds()));
    }

    @Test
    void profileIdValidationCoversEveryProtocolAndInventoryBoundary() {
        OreImportScreenState state = new OreImportScreenState(List.of("taken", "built_in"));
        state.acceptPreview(preview("commit-a", 0, 1, true, 1));
        assertAll(
                () -> assertEquals(OreImportScreenState.ProfileIdIssue.NONE, state.profileIdIssue()),
                () -> assertTrue(state.canCreate()));

        state.setTargetProfileId("   ");
        assertEquals(OreImportScreenState.ProfileIdIssue.EMPTY, state.profileIdIssue());
        state.setTargetProfileId("a".repeat(ProtocolLimits.ID_LENGTH));
        assertEquals(OreImportScreenState.ProfileIdIssue.NONE, state.profileIdIssue());
        state.setTargetProfileId("a".repeat(ProtocolLimits.ID_LENGTH + 1));
        assertEquals(OreImportScreenState.ProfileIdIssue.TOO_LONG, state.profileIdIssue());
        state.setTargetProfileId("Uppercase");
        assertEquals(OreImportScreenState.ProfileIdIssue.INVALID, state.profileIdIssue());
        state.setTargetProfileId("valid_name.with-dashes");
        assertEquals(OreImportScreenState.ProfileIdIssue.NONE, state.profileIdIssue());
        state.setTargetProfileId(" taken ");
        assertAll(
                () -> assertEquals("taken", state.targetProfileId()),
                () -> assertEquals(OreImportScreenState.ProfileIdIssue.EXISTS, state.profileIdIssue()),
                () -> assertFalse(state.canCreate()));

        state.setTargetProfileId("available");
        state.acceptPreview(preview("commit-invalid", 0, 1, false, 1));
        assertFalse(state.canCreate());
        state.acceptPreview(preview("commit-empty", 0, 1, true, 0));
        assertFalse(state.canCreate());
    }

    @Test
    void keyboardStyleLinePageHomeAndEndMovementRemainBounded() {
        OreImportScreenState state = state();
        state.acceptScan(scan("scan-a", 0, 8));
        state.resizeScanWindow(56);

        OreImportScreenState.PageTransition down = state.moveRows(1);
        OreImportScreenState.PageTransition pageDown = state.moveRows(state.visibleRows());
        OreImportScreenState.PageTransition boundaryDown = state.moveRows(1);

        assertAll(
                () -> assertTrue(down.localChanged()),
                () -> assertTrue(pageDown.localChanged()),
                () -> assertEquals(state.maximumLocalOffset(), state.localOffset()),
                () -> assertTrue(boundaryDown.requestsServerPage()),
                () -> assertEquals(1, boundaryDown.requestedPage()),
                () -> assertFalse(state.setLocalOffset(Integer.MAX_VALUE)),
                () -> assertTrue(state.setLocalOffset(0)),
                () -> assertFalse(state.setLocalOffset(Integer.MIN_VALUE)));

        OreImportScreenState.PageTransition boundaryUp = state.moveRows(-1);
        OreImportScreenState.PageTransition zero = state.moveRows(0);
        assertAll(
                () -> assertFalse(boundaryUp.localChanged()),
                () -> assertFalse(boundaryUp.requestsServerPage()),
                () -> assertFalse(zero.localChanged()),
                () -> assertFalse(zero.requestsServerPage()));
    }

    @Test
    void resizeMathReservesOneEmptyRowAndClampsOffsetsWhenMoreRowsBecomeVisible() {
        OreImportScreenState state = state();
        state.acceptScan(scan("empty", 0, 0));
        assertEquals(new OreImportScreenState.PageWindow(0, 0, 1), state.resizeScanWindow(10_000));

        state.acceptScan(scan("scan-a", 0, 4));
        assertEquals(new OreImportScreenState.PageWindow(0, 1, 1), state.resizeScanWindow(0));
        assertEquals(new OreImportScreenState.PageWindow(0, 2, 2), state.resizeScanWindow(56));
        state.moveRows(2);
        assertEquals(2, state.localOffset());
        assertEquals(new OreImportScreenState.PageWindow(0, 4, 4), state.resizeScanWindow(10_000));

        state.acceptPreview(preview("commit-a", 0, 2, true, 1));
        assertEquals(new OreImportScreenState.PageWindow(0, 1, 1), state.resizePreviewWindow(47));
        assertEquals(new OreImportScreenState.PageWindow(0, 2, 2), state.resizePreviewWindow(48));
    }

    private static OreImportScreenState state() {
        return new OreImportScreenState(List.of());
    }

    private static ScanView scan(String token, int page, int totalGroups) {
        int pageCount = Math.max(
                1,
                (totalGroups + ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE - 1)
                        / ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE);
        int start = page * ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE;
        int size = Math.min(ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE, Math.max(0, totalGroups - start));
        List<GroupView> groups = IntStream.range(start, start + size)
                .mapToObj(OreImportScreenStateTest::group)
                .toList();
        return new ScanView(token, 7L, "base", page, pageCount, totalGroups, 64, false, groups);
    }

    private static GroupView group(int index) {
        String material = "ore" + index;
        CandidateView candidate = new CandidateView(
                "example:" + material + "_ore", HostKind.STONE.replaceTag(), HostKind.STONE, Evidence.CONVENTIONAL_TAG);
        return new GroupView(
                "example:" + material, "example", material, Evidence.CONVENTIONAL_TAG, false, List.of(candidate));
    }

    private static PreviewView preview(String token, int page, int totalDiffEntries, boolean valid, long addedRules) {
        int pageCount = Math.max(
                1,
                (totalDiffEntries + ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE - 1)
                        / ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE);
        int start = page * ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE;
        int size = Math.min(ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE, Math.max(0, totalDiffEntries - start));
        List<DiffView> diff = IntStream.range(start, start + size)
                .mapToObj(index -> new DiffView(
                        "example:ore" + index,
                        DiffStatus.ADDED,
                        "example.ore" + index,
                        List.of("example:ore" + index + "_ore"),
                        List.of(),
                        ""))
                .toList();
        return new PreviewView(
                token,
                7L,
                "base",
                page,
                pageCount,
                totalDiffEntries,
                valid,
                addedRules,
                diff,
                List.of(),
                List.of(),
                false);
    }
}
