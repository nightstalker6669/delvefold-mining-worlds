package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.payload.BackupActionPayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DelvefoldBackupScreen extends DelvefoldScreen {
    private static final int PAGE_SIZE = 6;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final int page;
    private String armedId = "";
    private BackupOperation armedOperation;

    public DelvefoldBackupScreen(Screen parent, AdminSnapshot snapshot) {
        this(parent, snapshot, 0);
    }

    private DelvefoldBackupScreen(Screen parent, AdminSnapshot snapshot, int page) {
        super(Component.literal("Delvefold: World Backups"), snapshot);
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    public DelvefoldBackupScreen refreshed(AdminSnapshot updated) {
        Screen refreshedParent = this.parent instanceof DelvefoldDashboardScreen dashboard
                ? dashboard.refreshed(updated) : this.parent;
        return new DelvefoldBackupScreen(refreshedParent, updated, this.page);
    }

    @Override
    protected void initPanel() {
        int x = contentLeft() + 10;
        int y = contentTop() + 31;
        int inner = contentWidth() - 20;
        int pageCount = Math.max(1, (snapshot.backups().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(page, pageCount - 1);
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, snapshot.backups().size());
        for (int index = start; index < end; index++) {
            AdminSnapshot.BackupDraft backup = snapshot.backups().get(index);
            int pinWidth = 50;
            int restoreWidth = 68;
            int deleteWidth = 58;
            int gap = 4;
            int labelWidth = inner - pinWidth - restoreWidth - deleteWidth - gap * 3;
            String label = DATE.format(Instant.ofEpochMilli(backup.createdAtEpochMillis()))
                    + "  •  " + humanBytes(backup.sizeBytes()) + "  •  " + backup.terrain();
            Button info = addButton(x, y, labelWidth, 20, Component.literal(label),
                    backup.valid() ? Style.GHOST : Style.DANGER, ignored -> { });
            info.active = false;
            addButton(x + labelWidth + gap, y, pinWidth, 20,
                    Component.literal(backup.pinned() ? "Unpin" : "Pin"), Style.SECONDARY,
                    button -> perform(backup.pinned() ? BackupOperation.UNPIN : BackupOperation.PIN, backup.id()));
            Button restore = addButton(x + labelWidth + pinWidth + gap * 2, y, restoreWidth, 20,
                    Component.literal(armed(BackupOperation.RESTORE, backup.id()) ? "Confirm" : "Restore"),
                    Style.PRIMARY, button -> armOrPerform(BackupOperation.RESTORE, backup.id()));
            restore.active = backup.restorable() && snapshot.capabilities().canRestoreBackups();
            Button delete = addButton(x + labelWidth + pinWidth + restoreWidth + gap * 3, y,
                    deleteWidth, 20,
                    Component.literal(armed(BackupOperation.DELETE, backup.id()) ? "Confirm" : "Delete"),
                    Style.DANGER, button -> armOrPerform(BackupOperation.DELETE, backup.id()));
            delete.active = !backup.pinned() && snapshot.capabilities().canManageWorld();
            y += 24;
        }

        int footer = panelTop + panelHeight - 29;
        addButton(contentLeft(), footer, 72, 22, Component.translatable("gui.back"), Style.GHOST,
                button -> minecraft.setScreen(parent));
        if (pageCount > 1) {
            Button previous = addButton(contentLeft() + 78, footer, 58, 22, Component.literal("‹ Prev"),
                    Style.GHOST, button -> setPage(safePage - 1));
            previous.active = safePage > 0;
            Button next = addButton(contentLeft() + 142, footer, 58, 22, Component.literal("Next ›"),
                    Style.GHOST, button -> setPage(safePage + 1));
            next.active = safePage + 1 < pageCount;
        }
        if (snapshot.resetPending()) {
            addButton(contentRight() - 126, footer, 126, 22, Component.literal("Cancel pending"), Style.DANGER,
                    button -> perform(BackupOperation.CANCEL_RESTORE, ""));
        }
    }

    private boolean armed(BackupOperation operation, String id) {
        return armedOperation == operation && armedId.equals(id);
    }

    private void armOrPerform(BackupOperation operation, String id) {
        if (!armed(operation, id)) {
            armedOperation = operation;
            armedId = id;
            rebuildWidgets();
            return;
        }
        perform(operation, id);
    }

    private void perform(BackupOperation operation, String id) {
        DelvefoldClientRequests.send(new BackupActionPayload(snapshot.settingsRevision(), operation, id));
    }

    private void setPage(int replacement) {
        if (minecraft != null) {
            minecraft.setScreen(new DelvefoldBackupScreen(parent, snapshot, replacement));
        }
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentLeft();
        int y = contentTop();
        int width = contentWidth();
        int height = contentBottom() - y;
        drawCard(graphics, x, y, width, height);
        drawSectionTitle(graphics, Component.literal("RECOVERABLE WORLD SNAPSHOTS"), x + 10, y + 7);
        if (snapshot.backups().isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                    "No backups yet. Recreate or delete with backups enabled to create a restore point."),
                    x + 12, y + 35, width - 24, MUTED_TEXT);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
