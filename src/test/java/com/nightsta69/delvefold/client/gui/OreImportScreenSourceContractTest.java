package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the stable screen facade, network-authority boundary, and cached icon presentation after state extraction. */
class OreImportScreenSourceContractTest {
    private static final Path GUI = Path.of("src/main/java/com/nightsta69/delvefold/client/gui");

    @Test
    void screenRetainsItsPublicFacadeAndTokenBearingNetworkBoundary() throws IOException {
        String screen = compact(Files.readString(GUI.resolve("DelvefoldOreImportScreen.java")));
        String state = compact(Files.readString(GUI.resolve("OreImportScreenState.java")));

        assertTrue(screen.contains("publicfinalclassDelvefoldOreImportScreenextendsDelvefoldScreen"));
        assertTrue(screen.contains("publicDelvefoldOreImportScreen(Screenparent,AdminSnapshotsnapshot)"));
        assertTrue(screen.contains("publicvoidacceptScan(ScanViewreplacement)"));
        assertTrue(screen.contains("publicvoidacceptPreview(PreviewViewreplacement)"));
        assertTrue(screen.contains("requestOreImportScanPage(activeScan.scanToken(),transition.requestedPage())"));
        assertTrue(
                screen.contains("requestOreImportPreviewPage(activePreview.commitToken(),transition.requestedPage())"));
        assertTrue(
                screen.contains("createImportedOreProfile(activePreview.commitToken(),this.state.targetProfileId())"));
        assertFalse(state.contains("DelvefoldClientRequests"));
    }

    @Test
    void widgetRebuildStillCachesResolvedItemStacksAndKeyboardFocusStillProtectsTheEditBox() throws IOException {
        String screen = compact(Files.readString(GUI.resolve("DelvefoldOreImportScreen.java")));

        assertTrue(screen.contains("newRenderedGroup(icon(group),this.contentLeft()+5,rowY+4)"));
        assertTrue(screen.contains("for(RenderedGrouprendered:this.renderedGroups)"));
        assertTrue(screen.contains("if(this.getFocused()instanceofEditBox)"));
        assertTrue(screen.contains("returnsuper.keyPressed(keyCode,scanCode,modifiers)"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_DOWN){moveRows(1)"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_UP){moveRows(-1)"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_PAGE_DOWN){moveRows(this.state.visibleRows())"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_PAGE_UP){moveRows(-this.state.visibleRows())"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_HOME){setLocalOffset(0)"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_END){setLocalOffset(this.state.maximumLocalOffset())"));
        assertTrue(screen.contains("screen.delvefold.import.narration.preview"));
        assertTrue(screen.contains("screen.delvefold.import.narration.scan"));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
