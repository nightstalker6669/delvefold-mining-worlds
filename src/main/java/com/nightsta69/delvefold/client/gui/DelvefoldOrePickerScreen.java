package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.client.gui.widget.OreIconButton;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class DelvefoldOrePickerScreen extends DelvefoldScreen {
    private static final int TILE_STEP = 27;
    private static final TagKey<Block> COMMON_ORES =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private final Screen parent;
    private final List<OrePickerEntry> allEntries = new ArrayList<>();
    private final List<OrePickerEntry> filteredEntries = new ArrayList<>();
    private final List<OreIconButton> iconButtons = new ArrayList<>();
    private EditBox searchBox;
    private Button previousButton;
    private Button nextButton;
    private Button showAllButton;
    private boolean showAll;
    private int page;
    private int columns;
    private int rows;
    private int pageSize;
    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private String searchQuery = "";
    private Component localStatus = Component.empty();

    public DelvefoldOrePickerScreen(Screen parent, AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.ore_picker.title"), snapshot);
        this.parent = parent;
    }

    @Override
    protected void initPanel() {
        if (this.allEntries.isEmpty()) {
            loadRegistryEntries();
        }

        int x = this.contentLeft();
        int y = this.contentTop() + 22;
        int width = this.contentWidth();
        boolean compact = width < 430;
        int controlGap = 6;
        int showAllWidth = compact ? (width - controlGap) / 2 : 122;
        int idWidth = compact ? width - showAllWidth - controlGap : 94;
        int searchWidth = compact ? width : width - showAllWidth - idWidth - controlGap * 2;
        this.searchBox = this.addRenderableWidget(new EditBox(
                this.font, x, y, searchWidth, 20, Component.translatable("screen.delvefold.ore_picker.search")));
        this.searchBox.setMaxLength(128);
        this.searchBox.setHint(Component.translatable("screen.delvefold.ore_picker.search_hint"));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(value -> {
            this.searchQuery = value;
            this.localStatus = Component.empty();
            this.page = 0;
            updateGrid();
        });

        int controlsY = compact ? y + 26 : y;
        int showAllX = compact ? x : x + searchWidth + controlGap;
        this.showAllButton = this.addButton(
                showAllX,
                controlsY,
                showAllWidth,
                20,
                showAllLabel(),
                this.showAll ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.showAll = !this.showAll;
                    this.page = 0;
                    this.showAllButton.setMessage(showAllLabel());
                    setButtonStyle(this.showAllButton, this.showAll ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                    updateGrid();
                });
        int idX = showAllX + showAllWidth + controlGap;
        this.addButton(
                idX,
                controlsY,
                idWidth,
                20,
                Component.translatable("screen.delvefold.ore_picker.exact_id"),
                Style.SECONDARY,
                button -> chooseTypedId());

        this.columns = Math.max(6, Math.min(14, (width - 20) / TILE_STEP));
        this.gridTop = controlsY + 34;
        int availableGridHeight = this.contentBottom() - this.gridTop - 36;
        this.rows = Math.max(2, Math.min(5, availableGridHeight / TILE_STEP));
        this.pageSize = this.columns * this.rows;
        this.gridWidth = this.columns * TILE_STEP;
        this.gridLeft = this.panelLeft + (this.panelWidth - this.gridWidth) / 2;
        this.iconButtons.clear();
        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
                OreIconButton button = this.addRenderableWidget(new OreIconButton(
                        this.gridLeft + column * TILE_STEP + 1,
                        this.gridTop + row * TILE_STEP + 1,
                        25,
                        25,
                        this::choose));
                this.iconButtons.add(button);
            }
        }

        int pagerY = this.gridTop + this.rows * TILE_STEP + 8;
        this.previousButton = this.addButton(
                this.gridLeft,
                pagerY,
                68,
                20,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> {
                    this.page--;
                    updateGrid();
                });
        this.nextButton = this.addButton(
                this.gridLeft + this.gridWidth - 68,
                pagerY,
                68,
                20,
                Component.translatable("screen.delvefold.next"),
                Style.GHOST,
                button -> {
                    this.page++;
                    updateGrid();
                });
        this.addButton(
                this.contentLeft(),
                this.panelTop + this.panelHeight - 29,
                76,
                22,
                Component.translatable("gui.back"),
                Style.GHOST,
                button -> this.minecraft.setScreen(this.parent));

        updateGrid();
        this.setInitialFocus(this.searchBox);
    }

    private void loadRegistryEntries() {
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || block.asItem() == Items.AIR) {
                continue;
            }
            ItemStack icon = new ItemStack(block.asItem());
            boolean common = block.defaultBlockState().is(COMMON_ORES);
            boolean oreLike = common || id.getPath().contains("ore");
            this.allEntries.add(
                    new OrePickerEntry(id, block, icon, icon.getHoverName().getString(), common, oreLike));
        }
        this.allEntries.sort(Comparator.comparing(OrePickerEntry::commonTagged)
                .reversed()
                .thenComparing(entry -> entry.translatedName().toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.id().toString()));
    }

    private void updateGrid() {
        if (this.searchBox == null || this.iconButtons.isEmpty()) {
            return;
        }
        String query = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filteredEntries.clear();
        for (OrePickerEntry entry : this.allEntries) {
            if (!this.showAll && !entry.oreLike()) {
                continue;
            }
            String id = entry.id().toString().toLowerCase(Locale.ROOT);
            String name = entry.translatedName().toLowerCase(Locale.ROOT);
            if (!query.isEmpty()
                    && !id.contains(query)
                    && !entry.id().getNamespace().contains(query)
                    && !entry.id().getPath().contains(query)
                    && !name.contains(query)) {
                continue;
            }
            this.filteredEntries.add(entry);
        }

        int pageCount = Math.max(1, (this.filteredEntries.size() + this.pageSize - 1) / this.pageSize);
        this.page = Math.max(0, Math.min(this.page, pageCount - 1));
        int start = this.page * this.pageSize;
        for (int index = 0; index < this.iconButtons.size(); index++) {
            int entryIndex = start + index;
            this.iconButtons
                    .get(index)
                    .setEntry(entryIndex < this.filteredEntries.size() ? this.filteredEntries.get(entryIndex) : null);
        }
        this.previousButton.active = this.page > 0;
        this.nextButton.active = this.page + 1 < pageCount;
    }

    private Component showAllLabel() {
        return Component.translatable(
                this.showAll ? "screen.delvefold.ore_picker.all_blocks" : "screen.delvefold.ore_picker.ores_only");
    }

    private void choose(OrePickerEntry entry) {
        chooseBlock(entry.id());
    }

    private void chooseTypedId() {
        ResourceLocation id = ResourceLocation.tryParse(this.searchQuery.trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            this.localStatus = Component.translatable("screen.delvefold.ore_picker.no_exact_match");
            return;
        }
        chooseBlock(id);
    }

    private void chooseBlock(ResourceLocation id) {
        AdminSnapshot.OreRuleDraft draft = AdminSnapshot.OreRuleDraft.createDefault(id.toString(), inferredHost(id));
        this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(this, this.snapshot, draft));
    }

    private static String inferredHost(ResourceLocation blockId) {
        return blockId.getPath().startsWith("deepslate_")
                ? "minecraft:deepslate_ore_replaceables"
                : "minecraft:stone_ore_replaceables";
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.ore_picker.registry"),
                this.contentLeft(),
                this.contentTop(),
                MUTED_TEXT,
                false);
        int gridCardY = this.gridTop - 7;
        this.drawCard(graphics, this.gridLeft - 7, gridCardY, this.gridWidth + 14, this.rows * TILE_STEP + 14);
        int pageCount = Math.max(1, (this.filteredEntries.size() + this.pageSize - 1) / this.pageSize);
        Component resultText = Component.translatable(
                "screen.delvefold.ore_picker.results", this.filteredEntries.size(), this.page + 1, pageCount);
        graphics.drawCenteredString(
                this.font,
                resultText,
                this.panelLeft + this.panelWidth / 2,
                this.gridTop + this.rows * TILE_STEP + 14,
                MUTED_TEXT);
        if (!this.localStatus.getString().isEmpty()) {
            graphics.drawString(
                    this.font,
                    this.localStatus,
                    this.contentLeft() + 88,
                    this.panelTop + this.panelHeight - 22,
                    DANGER,
                    false);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public Component getNarrationMessage() {
        int safePageSize = Math.max(1, this.pageSize);
        int pageCount = Math.max(1, (this.filteredEntries.size() + safePageSize - 1) / safePageSize);
        return Component.translatable(
                "screen.delvefold.ore_picker.narration",
                this.filteredEntries.size(),
                this.page + 1,
                pageCount,
                Component.translatable(
                        this.showAll
                                ? "screen.delvefold.ore_picker.all_blocks"
                                : "screen.delvefold.ore_picker.ores_only"));
    }
}
