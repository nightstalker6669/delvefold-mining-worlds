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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DelvefoldBackupScreen extends DelvefoldScreen {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final int page;
    private String armedId = "";
    private BackupOperation armedOperation;

    public DelvefoldBackupScreen(Screen parent, AdminSnapshot snapshot) {
        this(parent, snapshot, 0);
    }

    private DelvefoldBackupScreen(Screen parent, AdminSnapshot snapshot, int page) {
        super(Component.translatable("screen.delvefold.backup.title"), snapshot);
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    public DelvefoldBackupScreen refreshed(AdminSnapshot updated) {
        Screen refreshedParent =
                this.parent instanceof DelvefoldDashboardScreen dashboard ? dashboard.refreshed(updated) : this.parent;
        return new DelvefoldBackupScreen(refreshedParent, updated, this.page);
    }

    @Override
    protected void initPanel() {
        BackupScreenLayout layout = layout();
        boolean canManageWorld = snapshot.capabilities().canManageWorld();
        int pageSize = layout.pageSize();
        int pageCount = Math.max(1, (snapshot.backups().size() + pageSize - 1) / pageSize);
        int safePage = Math.min(page, pageCount - 1);
        int start = safePage * pageSize;
        int end = Math.min(start + pageSize, snapshot.backups().size());
        for (int index = start; index < end; index++) {
            AdminSnapshot.BackupDraft backup = snapshot.backups().get(index);
            int row = index - start;
            BackupScreenLayout.Bounds infoBounds = layout.infoBounds(row);
            BackupScreenLayout.Bounds pinBounds = layout.actionBounds(row, 0);
            BackupScreenLayout.Bounds verifyBounds = layout.actionBounds(row, 1);
            BackupScreenLayout.Bounds restoreBounds = layout.actionBounds(row, 2);
            BackupScreenLayout.Bounds deleteBounds = layout.actionBounds(row, 3);
            Component integrity =
                    Component.translatable("screen.delvefold.backup.integrity." + backup.integrityState());
            Component label = Component.translatable(
                    "screen.delvefold.backup.row",
                    Component.literal(DATE.format(Instant.ofEpochMilli(backup.createdAtEpochMillis()))),
                    humanBytes(backup.sizeBytes()),
                    DelvefoldText.option("terrain", backup.terrain()),
                    integrity);
            Button info = addButton(
                    infoBounds.x(),
                    infoBounds.y(),
                    infoBounds.width(),
                    infoBounds.height(),
                    label,
                    backup.valid() ? Style.GHOST : Style.DANGER,
                    ignored -> {});
            info.setTooltip(Tooltip.create(Component.translatable(
                    "screen.delvefold.backup.details", backup.id(), operationLabel(backup.operation()), integrity)));
            Button pin = addButton(
                    pinBounds.x(),
                    pinBounds.y(),
                    pinBounds.width(),
                    pinBounds.height(),
                    Component.translatable(
                            backup.pinned() ? "screen.delvefold.backup.unpin" : "screen.delvefold.backup.pin"),
                    Style.SECONDARY,
                    button -> perform(backup.pinned() ? BackupOperation.UNPIN : BackupOperation.PIN, backup.id()));
            pin.active = canManageWorld;
            pin.setTooltip(Tooltip.create(Component.translatable(
                    canManageWorld
                            ? "screen.delvefold.backup.pin.tooltip"
                            : "screen.delvefold.backup.manage_permission")));
            Button verify = addButton(
                    verifyBounds.x(),
                    verifyBounds.y(),
                    verifyBounds.width(),
                    verifyBounds.height(),
                    Component.translatable("screen.delvefold.backup.verify"),
                    Style.SECONDARY,
                    button -> perform(BackupOperation.VERIFY, backup.id()));
            verify.active = backup.valid() && canManageWorld;
            verify.setTooltip(Tooltip.create(Component.translatable(
                    !canManageWorld
                            ? "screen.delvefold.backup.manage_permission"
                            : backup.valid()
                                    ? "screen.delvefold.backup.verify.tooltip"
                                    : "screen.delvefold.backup.verify.tooltip.invalid")));
            Button restore = addButton(
                    restoreBounds.x(),
                    restoreBounds.y(),
                    restoreBounds.width(),
                    restoreBounds.height(),
                    Component.translatable(
                            armed(BackupOperation.RESTORE, backup.id())
                                    ? "screen.delvefold.confirm"
                                    : "screen.delvefold.backup.restore"),
                    Style.PRIMARY,
                    button -> armOrPerform(BackupOperation.RESTORE, backup.id()));
            restore.active = backup.restorable() && snapshot.capabilities().canRestoreBackups();
            restore.setTooltip(Tooltip.create(Component.translatable(
                    backup.restorable()
                            ? "screen.delvefold.backup.restore.tooltip"
                            : "screen.delvefold.backup.restore.tooltip.unavailable")));
            Button delete = addButton(
                    deleteBounds.x(),
                    deleteBounds.y(),
                    deleteBounds.width(),
                    deleteBounds.height(),
                    Component.translatable(
                            armed(BackupOperation.DELETE, backup.id())
                                    ? "screen.delvefold.confirm"
                                    : "screen.delvefold.delete"),
                    Style.DANGER,
                    button -> armOrPerform(BackupOperation.DELETE, backup.id()));
            delete.active = !backup.pinned() && canManageWorld;
            delete.setTooltip(Tooltip.create(Component.translatable(
                    !canManageWorld
                            ? "screen.delvefold.backup.manage_permission"
                            : backup.pinned()
                                    ? "screen.delvefold.backup.delete.tooltip.pinned"
                                    : "screen.delvefold.backup.delete.tooltip")));
        }

        BackupScreenLayout.Footer footer = layout.footer(pageCount > 1, snapshot.restorePending());
        addButton(
                footer.back().x(),
                footer.back().y(),
                footer.back().width(),
                footer.back().height(),
                Component.translatable("gui.back"),
                Style.GHOST,
                button -> minecraft.setScreen(parent));
        if (pageCount > 1) {
            Button previous = addButton(
                    footer.previous().x(),
                    footer.previous().y(),
                    footer.previous().width(),
                    footer.previous().height(),
                    Component.translatable("screen.delvefold.previous"),
                    Style.GHOST,
                    button -> setPage(safePage - 1));
            previous.active = safePage > 0;
            Button next = addButton(
                    footer.next().x(),
                    footer.next().y(),
                    footer.next().width(),
                    footer.next().height(),
                    Component.translatable("screen.delvefold.next"),
                    Style.GHOST,
                    button -> setPage(safePage + 1));
            next.active = safePage + 1 < pageCount;
        }
        if (snapshot.restorePending()) {
            Button cancelRestore = addButton(
                    footer.cancel().x(),
                    footer.cancel().y(),
                    footer.cancel().width(),
                    footer.cancel().height(),
                    Component.translatable("screen.delvefold.backup.cancel_pending"),
                    Style.DANGER,
                    button -> perform(BackupOperation.CANCEL_RESTORE, ""));
            cancelRestore.active = canManageWorld;
            if (!canManageWorld) {
                cancelRestore.setTooltip(
                        Tooltip.create(Component.translatable("screen.delvefold.backup.manage_permission")));
            }
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
        drawSectionTitle(graphics, Component.translatable("screen.delvefold.backup.snapshots"), x + 10, y + 7);
        if (snapshot.backups().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("screen.delvefold.backup.empty"),
                    x + 12,
                    y + 35,
                    width - 24,
                    MUTED_TEXT);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public Component getNarrationMessage() {
        int pageSize = layout().pageSize();
        int pageCount = Math.max(1, (this.snapshot.backups().size() + pageSize - 1) / pageSize);
        int safePage = Math.min(this.page, pageCount - 1);
        Component armed = this.armedOperation == null
                ? Component.translatable("screen.delvefold.backup.armed.none")
                : Component.translatable(
                        "screen.delvefold.backup.armed.operation",
                        operationLabel(this.armedOperation.name().toLowerCase(Locale.ROOT)),
                        this.armedId);
        return Component.translatable(
                "screen.delvefold.backup.narration", this.snapshot.backups().size(), safePage + 1, pageCount, armed);
    }

    private BackupScreenLayout layout() {
        if (this.panelWidth > 0 && this.panelHeight > 0) {
            return BackupScreenLayout.forPanel(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
        }
        return BackupScreenLayout.forScreen(Math.max(1, this.width), Math.max(1, this.height));
    }

    private static Component operationLabel(String operation) {
        String safe = operation == null ? "unknown" : operation.toLowerCase(Locale.ROOT);
        return Component.translatable("screen.delvefold.backup.operation." + safe);
    }

    private static Component humanBytes(long bytes) {
        if (bytes < 0) {
            return Component.translatable("screen.delvefold.backup.size_unknown");
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return Component.translatable(
                "screen.delvefold.backup.size", String.format(Locale.ROOT, "%.1f", value), units[unit]);
    }
}
