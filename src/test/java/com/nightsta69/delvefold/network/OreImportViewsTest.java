package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.model.OreImportViews.CandidateView;
import com.nightsta69.delvefold.network.model.OreImportViews.GroupView;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreImportViewsTest {
    @Test
    void protocolFourteenLocksTheExpandedHostKindOrdinalOrder() {
        assertArrayEquals(
                new HostKind[] {
                    HostKind.STONE,
                    HostKind.DEEPSLATE,
                    HostKind.NETHERRACK,
                    HostKind.END_STONE,
                    HostKind.REVIEW_REQUIRED
                },
                HostKind.values());
        for (HostKind hostKind : HostKind.values()) {
            assertEquals(hostKind, HostKind.fromWireId(hostKind.wireId()));
        }
        assertThrows(IllegalArgumentException.class, () -> HostKind.fromWireId(-1));
        assertThrows(IllegalArgumentException.class, () -> HostKind.fromWireId(5));
    }

    @Test
    void emptyViewsUseOneCompletePage() {
        assertDoesNotThrow(() -> new ScanView("token", 0L, "base", 0, 1, 0, 0, false, List.of()));
        assertDoesNotThrow(
                () -> new PreviewView("token", 0L, "base", 0, 1, 0, true, 0L, List.of(), List.of(), List.of(), false));
    }

    @Test
    void scanRejectsOutOfRangeAndIncompletePages() {
        assertThrows(
                IllegalArgumentException.class, () -> new ScanView("token", 0L, "base", 1, 1, 0, 0, false, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new ScanView("token", 0L, "base", 0, 2, 0, 0, false, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new ScanView("token", 0L, "base", 0, 1, 1, 0, false, List.of()));
    }

    @Test
    void scanRejectsDuplicateGroupAndCandidateIds() {
        GroupView group = group("example:tin", List.of(candidate("example:tin_ore")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScanView("token", 0L, "base", 0, 1, 2, 2, false, List.of(group, group)));
        assertThrows(
                IllegalArgumentException.class,
                () -> group("example:tin", List.of(candidate("example:tin_ore"), candidate("example:tin_ore"))));
    }

    @Test
    void previewRejectsOutOfRangeAndIncompletePages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewView("token", 0L, "base", 1, 1, 0, true, 0L, List.of(), List.of(), List.of(), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewView("token", 0L, "base", 0, 2, 0, true, 0L, List.of(), List.of(), List.of(), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewView("token", 0L, "base", 0, 1, 1, true, 0L, List.of(), List.of(), List.of(), false));
    }

    private static GroupView group(String id, List<CandidateView> candidates) {
        String[] parts = id.split(":", 2);
        return new GroupView(id, parts[0], parts[1], Evidence.CONVENTIONAL_TAG, false, candidates);
    }

    private static CandidateView candidate(String id) {
        return new CandidateView(id, HostKind.STONE.replaceTag(), HostKind.STONE, Evidence.CONVENTIONAL_TAG);
    }
}
