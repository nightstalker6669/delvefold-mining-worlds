package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackupScreenLayoutTest {
    @ParameterizedTest(name = "{0}x{1} at GUI scale {2}")
    @CsvSource({
            "854,480,2,427,240,2,STACKED",
            "1920,1080,4,480,270,5,INLINE"
    })
    void effectiveCompactResolutionsKeepEveryBackupActionAboveTheFooter(
            int physicalWidth,
            int physicalHeight,
            int guiScale,
            int expectedWidth,
            int expectedHeight,
            int expectedRows,
            BackupScreenLayout.ActionMode expectedMode) {
        int width = physicalWidth / guiScale;
        int height = physicalHeight / guiScale;
        assertEquals(expectedWidth, width);
        assertEquals(expectedHeight, height);

        BackupScreenLayout layout = BackupScreenLayout.forScreen(width, height);

        assertEquals(expectedRows, layout.pageSize());
        assertEquals(expectedMode, layout.actionMode());
        assertTrue(layout.listBottom() < layout.footerTop());
        assertTrue(layout.footerY() >= layout.footerTop());
        for (int row = 0; row < layout.pageSize(); row++) {
            assertRowContained(layout, row);
        }
    }

    @Test
    void veryNarrowPanelsUseTwoActionColumnsInsteadOfCrushingLabels() {
        BackupScreenLayout layout = BackupScreenLayout.forScreen(320, 240);

        assertEquals(BackupScreenLayout.ActionMode.GRID, layout.actionMode());
        assertEquals(1, layout.pageSize());
        for (int action = 0; action < 4; action++) {
            assertTrue(layout.actionBounds(0, action).width() >= 120);
        }
        assertRowContained(layout, 0);
    }

    @Test
    void pendingRestoreAndPaginationShareThe320By240FooterWithoutOverlap() {
        BackupScreenLayout layout = BackupScreenLayout.forScreen(320, 240);
        BackupScreenLayout.Footer footer = layout.footer(true, true);

        assertTrue(footer.compact());
        assertFalse(footer.wrapped());
        assertFooterContainedAndSeparated(layout, footer);
    }

    @Test
    void narrowerPendingFooterWrapsCancelAboveNavigationWithoutTouchingRows() {
        BackupScreenLayout layout = BackupScreenLayout.forScreen(240, 240);
        BackupScreenLayout.Footer footer = layout.footer(true, true);

        assertTrue(footer.compact());
        assertTrue(footer.wrapped());
        assertFooterContainedAndSeparated(layout, footer);
        assertTrue(layout.actionBounds(layout.pageSize() - 1, 3).bottom() < footer.cancel().y());
    }

    @Test
    void screenUsesTheResponsivePageSizeAndSharedActionGeometry() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldBackupScreen.java"));

        assertTrue(source.contains("BackupScreenLayout layout = layout();"));
        assertTrue(source.contains("int pageSize = layout.pageSize();"));
        assertTrue(source.contains("layout.actionBounds(row, 3)"));
        assertTrue(source.contains("layout.footer(pageCount > 1, snapshot.restorePending())"));
        assertFalse(source.contains("PAGE_SIZE"),
                "A fixed backup page size would overlap the footer again at compact GUI scales");
    }

    @Test
    void everyWorldManagementBackupActionReflectsTheServerCapability() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldBackupScreen.java"));

        assertTrue(source.contains("boolean canManageWorld = snapshot.capabilities().canManageWorld();"));
        assertTrue(source.contains("pin.active = canManageWorld;"));
        assertTrue(source.contains("verify.active = backup.valid() && canManageWorld;"));
        assertTrue(source.contains("delete.active = !backup.pinned() && canManageWorld;"));
        assertTrue(source.contains("cancelRestore.active = canManageWorld;"));
        assertTrue(source.contains("screen.delvefold.backup.manage_permission"),
                "Disabled management actions need a non-color permission explanation");
    }

    @Test
    void eachScreenOnlyOffersTheCancellationOwnedByItsPendingOperation() throws IOException {
        String backups = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldBackupScreen.java"));
        String dashboard = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldDashboardScreen.java"));

        assertTrue(backups.contains("if (snapshot.restorePending())"));
        assertTrue(dashboard.contains("if (this.snapshot.worldOperationPending())"));
        assertFalse(backups.contains("snapshot.resetPending()"));
        assertFalse(dashboard.contains("snapshot.resetPending()"));
    }

    private static void assertFooterContainedAndSeparated(
            BackupScreenLayout layout, BackupScreenLayout.Footer footer) {
        int left = layout.listX() - 10;
        int right = layout.listX() + layout.listWidth() + 10;
        BackupScreenLayout.Bounds[] bounds = {
                footer.back(), footer.previous(), footer.next(), footer.cancel()
        };
        for (BackupScreenLayout.Bounds button : bounds) {
            assertTrue(button.x() >= left);
            assertTrue(button.right() <= right);
        }
        for (int first = 0; first < bounds.length; first++) {
            for (int second = first + 1; second < bounds.length; second++) {
                assertFalse(bounds[first].overlaps(bounds[second]));
            }
        }
    }

    private static void assertRowContained(BackupScreenLayout layout, int row) {
        BackupScreenLayout.Bounds info = layout.infoBounds(row);
        assertContained(layout, info);
        assertFalse(info.bottom() > layout.listBottom());

        BackupScreenLayout.Bounds[] actions = new BackupScreenLayout.Bounds[4];
        for (int action = 0; action < actions.length; action++) {
            actions[action] = layout.actionBounds(row, action);
            assertContained(layout, actions[action]);
            assertTrue(actions[action].bottom() <= layout.listBottom());
            assertFalse(info.overlaps(actions[action]));
        }
        for (int left = 0; left < actions.length; left++) {
            for (int right = left + 1; right < actions.length; right++) {
                assertFalse(actions[left].overlaps(actions[right]));
            }
        }
    }

    private static void assertContained(
            BackupScreenLayout layout, BackupScreenLayout.Bounds bounds) {
        assertTrue(bounds.x() >= layout.listX());
        assertTrue(bounds.right() <= layout.listX() + layout.listWidth());
        assertTrue(bounds.y() >= layout.listTop());
    }
}
