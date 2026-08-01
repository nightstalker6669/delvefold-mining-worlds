package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the stable screen facade and allocation-free layout delegation after dashboard state extraction. */
class DashboardRefactorSourceContractTest {
    private static final Path GUI = Path.of("src/main/java/com/nightsta69/delvefold/client/gui");

    @Test
    void screenCachesOneResponsiveLayoutPerInitialization() throws IOException {
        String screen = Files.readString(GUI.resolve("DelvefoldDashboardScreen.java"));

        assertTrue(screen.contains("public final class DelvefoldDashboardScreen extends DelvefoldScreen"));
        assertTrue(screen.contains("private @Nullable DashboardTabLayout tabLayout;"));
        assertTrue(screen.contains("this.tabLayout = new DashboardTabLayout("));
        assertEquals(1, occurrences(screen, "new DashboardTabLayout("));
        assertFalse(screen.contains("return new DashboardTabLayout("));
        assertTrue(screen.contains("return dashboardLayout().bodyTop();"));
        assertTrue(screen.contains("return dashboardLayout().bodyViewportTop();"));
        assertTrue(screen.contains("this.footerButtonY()"));
        assertFalse(screen.contains("private int footerY()"));
    }

    @Test
    void extractedCollaboratorsRemainPackagePrivateBehindThePublicScreen() throws IOException {
        for (String name :
                new String[] {"DashboardDraftState.java", "DashboardPresentation.java", "DashboardTabLayout.java"}) {
            String source = Files.readString(GUI.resolve(name));
            assertFalse(source.contains("public record "), () -> name + " must remain package-private");
            assertFalse(source.contains("public class "), () -> name + " must remain package-private");
        }
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
