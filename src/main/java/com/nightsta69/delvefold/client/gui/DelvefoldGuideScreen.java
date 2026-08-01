package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.guide.GuideSnapshot;
import com.nightsta69.delvefold.guide.GuideSnapshot.HeightBand;
import com.nightsta69.delvefold.guide.GuideSnapshot.OreEntry;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Responsive, scrollable, read-only rendering of the server-authorized Seam Ledger snapshot. */
public final class DelvefoldGuideScreen extends Screen {
    private static final int BACKGROUND = 0xF20E151A;
    private static final int SURFACE = 0xF21A242B;
    private static final int SURFACE_ALT = 0xE6162026;
    private static final int BORDER = 0xFF4B626B;
    private static final int CARD_BORDER = 0xFF34464F;
    private static final int TEXT = 0xFFF0F6F7;
    private static final int MUTED = 0xFFA6B5BC;
    private static final int DIM = 0xFF73858E;
    private static final int ACCENT = 0xFF64D6B2;
    private static final int SUCCESS = 0xFF72D6A7;
    private static final int WARNING = 0xFFFFC766;
    private static final int ROW_HEIGHT = 62;

    private final GuideSnapshot snapshot;
    private final long receivedAtMillis;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int footerTextWidth;
    private int scrollOffset;
    private OreEntry hoveredOre;

    public DelvefoldGuideScreen(GuideSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.guide.title"));
        this.snapshot = snapshot;
        this.receivedAtMillis = Util.getMillis();
    }

    @Override
    protected void init() {
        GuideLayout layout = GuideLayout.calculate(this.width, this.height);
        this.panelWidth = layout.panelWidth();
        this.panelHeight = layout.panelHeight();
        this.panelLeft = layout.panelLeft();
        this.panelTop = layout.panelTop();
        this.listTop = layout.listTop();
        this.listBottom = layout.listBottom();
        this.footerTextWidth = layout.footerTextWidth();
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, maximumScroll());

        Button done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(layout.doneX(), layout.doneY(), layout.doneWidth(), layout.doneHeight())
                .build());
        this.setInitialFocus(done);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics);
        drawOverview(graphics);
        this.hoveredOre = null;
        drawOreRows(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredOre != null) {
            graphics.renderTooltip(this.font, wrappedTooltip(this.hoveredOre), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, this.width, this.height, 0xB00B1116, 0xE005080B);
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(this.panelLeft + 5, this.panelTop + 6,
                this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 6, 0x88000000);
        graphics.fill(this.panelLeft, this.panelTop,
                this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BACKGROUND);
        graphics.fillGradient(this.panelLeft + 1, this.panelTop + 1,
                this.panelLeft + this.panelWidth - 1, this.panelTop + 43, 0xFF1E3035, 0xFF142128);
        graphics.fill(this.panelLeft + 1, this.panelTop + this.panelHeight - 38,
                this.panelLeft + this.panelWidth - 1, this.panelTop + this.panelHeight - 1, 0xFF10191F);
        graphics.fill(this.panelLeft + 1, this.panelTop + 1,
                this.panelLeft + this.panelWidth - 1, this.panelTop + 4, ACCENT);
        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, BORDER);
        graphics.drawString(this.font, this.title, this.panelLeft + 14, this.panelTop + 16, TEXT, false);
        if (this.panelWidth >= 430) {
            Component count = Component.translatable("screen.delvefold.guide.ore_count", this.snapshot.ores().size());
            graphics.drawString(this.font, count,
                    this.panelLeft + this.panelWidth - 14 - this.font.width(count), this.panelTop + 16, MUTED, false);
        }

        Component footer = this.snapshot.truncated()
                ? Component.translatable("screen.delvefold.guide.truncated")
                : Component.translatable("screen.delvefold.guide.server_authoritative");
        drawFitted(graphics, footer, this.panelLeft + 14,
                this.panelTop + this.panelHeight - 25, this.footerTextWidth,
                this.snapshot.truncated() ? WARNING : DIM);
    }

    private void drawOverview(GuiGraphics graphics) {
        int x = this.panelLeft + 14;
        int y = this.panelTop + 50;
        int width = this.panelWidth - 28;
        graphics.fill(x, y, x + width, y + 55, SURFACE);
        graphics.renderOutline(x, y, width, 55, CARD_BORDER);
        graphics.fill(x + 8, y + 8, x + 11, y + 46, ACCENT);

        drawFitted(graphics, Component.literal(this.snapshot.worldName()), x + 18, y + 8, width - 28, TEXT);
        Component terrain = Component.translatable("screen.delvefold.guide.terrain_profile_geology",
                terrainName(this.snapshot.terrain()), terrainVariantName(this.snapshot.terrainVariant()),
                geologyThemeName(this.snapshot.geologyTheme()), this.snapshot.activeProfile());
        drawFitted(graphics, terrain, x + 18, y + 23, width - 28, MUTED);
        Component status = Component.translatable("screen.delvefold.guide.status_line",
                Component.translatable("screen.delvefold.guide.portal."
                        + this.snapshot.portalStatus().name().toLowerCase(Locale.ROOT)),
                renewalText(this.snapshot.renewal()));
        drawFitted(graphics, status, x + 18, y + 38, width - 28,
                this.snapshot.portalStatus() == GuideSnapshot.PortalStatus.AVAILABLE ? SUCCESS : WARNING);
    }

    private void drawOreRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.panelLeft + 14;
        int width = this.panelWidth - 28;
        if (this.listBottom <= this.listTop || width <= 0) {
            return;
        }
        if (this.snapshot.ores().isEmpty()) {
            Component empty = Component.translatable("screen.delvefold.guide.empty");
            graphics.drawCenteredString(this.font, empty, x + width / 2,
                    this.listTop + Math.max(4, (this.listBottom - this.listTop - this.font.lineHeight) / 2), MUTED);
            return;
        }
        graphics.enableScissor(x, this.listTop, x + width, this.listBottom);
        for (int index = 0; index < this.snapshot.ores().size(); index++) {
            int y = this.listTop + index * ROW_HEIGHT - this.scrollOffset;
            if (y + ROW_HEIGHT <= this.listTop || y >= this.listBottom) {
                continue;
            }
            drawOreRow(graphics, this.snapshot.ores().get(index), x, y, width,
                    mouseX >= x && mouseX < x + width
                            && mouseY >= this.listTop && mouseY < this.listBottom
                            && mouseY >= y && mouseY < y + ROW_HEIGHT - 4);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x + width - 4);
    }

    private void drawOreRow(GuiGraphics graphics, OreEntry ore, int x, int y, int width, boolean hovered) {
        int bottom = y + ROW_HEIGHT - 4;
        if (hovered) {
            this.hoveredOre = ore;
        }
        graphics.fill(x, y, x + width, bottom, hovered ? 0xF024343C : SURFACE_ALT);
        graphics.renderOutline(x, y, width, ROW_HEIGHT - 4,
                ore.applicability().appliesToActiveTerrain() ? CARD_BORDER : 0xFF4B4040);

        int textX = x + 9;
        ItemStack icon = iconFor(ore);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 8, y + 7);
            textX += 21;
        }
        Component frequency = ore.truncated()
                ? Component.translatable("screen.delvefold.guide.frequency_limited", frequency(ore))
                : frequency(ore);
        int frequencyWidth = Math.min(128, Math.max(60, this.font.width(frequency) + 6));
        drawFitted(graphics, Component.literal(ore.ruleId()), textX, y + 7,
                width - (textX - x) - frequencyWidth - 12,
                ore.applicability().appliesToActiveTerrain() ? TEXT : DIM);
        drawFitted(graphics, frequency, x + width - frequencyWidth - 7, y + 7,
                frequencyWidth, ore.applicability().appliesToActiveTerrain() ? ACCENT : DIM);

        String outputs = ore.outputs().stream()
                .map(output -> output.kind() == OutputKind.BLOCK_TAG ? "#" + output.sourceId() : output.sourceId())
                .collect(Collectors.joining(", "));
        drawFitted(graphics, Component.translatable("screen.delvefold.guide.outputs", outputs),
                textX, y + 21, width - (textX - x) - 10, MUTED);

        String heights = ore.heightBands().stream().limit(3)
                .map(this::heightSummary)
                .collect(Collectors.joining("  •  "));
        if (ore.heightBands().size() > 3) {
            heights += "  +" + (ore.heightBands().size() - 3);
        }
        drawFitted(graphics, Component.translatable("screen.delvefold.guide.heights",
                heights.isBlank() ? Component.translatable("screen.delvefold.guide.none") : heights),
                textX, y + 34, width - (textX - x) - 10, MUTED);

        String terrains = ore.applicability().terrains().stream().map(DelvefoldGuideScreen::terrainName)
                .map(Component::getString)
                .collect(Collectors.joining(", "));
        String biomes = biomeSummary(ore);
        Component applicability = Component.translatable("screen.delvefold.guide.applicability",
                terrains, biomes);
        drawFitted(graphics, applicability, textX, y + 47,
                width - (textX - x) - 10, ore.applicability().appliesToActiveTerrain() ? DIM : WARNING);
    }

    private void drawScrollbar(GuiGraphics graphics, int x) {
        int maximum = maximumScroll();
        if (maximum <= 0 || this.listBottom <= this.listTop) {
            return;
        }
        int trackHeight = this.listBottom - this.listTop;
        int contentHeight = this.snapshot.ores().size() * ROW_HEIGHT;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = this.listTop + this.scrollOffset * thumbTravel / maximum;
        graphics.fill(x, this.listTop, x + 3, this.listBottom, 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.panelLeft && mouseX < this.panelLeft + this.panelWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            this.scrollOffset = Math.clamp(this.scrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT,
                    0, maximumScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = Math.max(ROW_HEIGHT, this.listBottom - this.listTop - ROW_HEIGHT);
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.scrollOffset = Math.clamp(this.scrollOffset + ROW_HEIGHT, 0, maximumScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.scrollOffset = Math.clamp(this.scrollOffset - ROW_HEIGHT, 0, maximumScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.scrollOffset = Math.clamp(this.scrollOffset + page, 0, maximumScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.scrollOffset = Math.clamp(this.scrollOffset - page, 0, maximumScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            this.scrollOffset = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            this.scrollOffset = maximumScroll();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int maximumScroll() {
        return Math.max(0, this.snapshot.ores().size() * ROW_HEIGHT - Math.max(0, this.listBottom - this.listTop));
    }

    private ItemStack iconFor(OreEntry ore) {
        for (GuideSnapshot.Output output : ore.outputs()) {
            ResourceLocation id = ResourceLocation.tryParse(output.iconBlockId());
            if (id == null) {
                continue;
            }
            var block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block != null && !block.asItem().getDefaultInstance().isEmpty()) {
                return block.asItem().getDefaultInstance();
            }
        }
        return ItemStack.EMPTY;
    }

    private String heightSummary(HeightBand band) {
        String best = band.bestMinY() == band.bestMaxY()
                ? Integer.toString(band.bestMinY())
                : band.bestMinY() + ".." + band.bestMaxY();
        String summary = "Y " + best + " " + distributionName(band.distribution()).getString();
        return provinceBand(band) ? summary : summary + " ×" + band.veinSize();
    }

    private Component fullHeightSummary(HeightBand band) {
        String best = band.bestMinY() == band.bestMaxY()
                ? Integer.toString(band.bestMinY())
                : band.bestMinY() + ".." + band.bestMaxY();
        if (provinceBand(band)) {
            return Component.translatable("screen.delvefold.guide.tooltip.province_band_detail",
                    band.bandId(), band.minY(), band.maxY(), best,
                    distributionName(band.distribution()));
        }
        return Component.translatable("screen.delvefold.guide.tooltip.band_detail",
                band.bandId(), band.minY(), band.maxY(), best,
                distributionName(band.distribution()), band.veinSize());
    }

    private String biomeSummary(OreEntry ore) {
        String includes = ore.applicability().biomeIncludes().isEmpty()
                ? Component.translatable("screen.delvefold.guide.all_mining_biomes").getString()
                : String.join(", ", ore.applicability().biomeIncludes());
        if (ore.applicability().biomeExcludes().isEmpty()) {
            return includes;
        }
        return Component.translatable("screen.delvefold.guide.biomes_excluding",
                includes, String.join(", ", ore.applicability().biomeExcludes())).getString();
    }

    private List<Component> tooltip(OreEntry ore) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(ore.ruleId()));
        lines.add(Component.translatable("screen.delvefold.guide.tooltip.frequency", frequency(ore)));
        for (GuideSnapshot.Output output : ore.outputs()) {
            String source = output.kind() == OutputKind.BLOCK_TAG ? "#" + output.sourceId() : output.sourceId();
            String icon = output.iconBlockId().isBlank() ? "" : "  [" + output.iconBlockId() + "]";
            lines.add(Component.translatable("screen.delvefold.guide.tooltip.output", source + icon));
        }
        for (HeightBand band : ore.heightBands()) {
            lines.add(Component.translatable("screen.delvefold.guide.tooltip.band", fullHeightSummary(band)));
        }
        lines.add(Component.translatable("screen.delvefold.guide.tooltip.terrains",
                ore.applicability().terrains().stream().map(DelvefoldGuideScreen::terrainName)
                        .map(Component::getString).collect(Collectors.joining(", "))));
        lines.add(Component.translatable("screen.delvefold.guide.tooltip.biomes", biomeSummary(ore)));
        if (ore.truncated()) {
            lines.add(Component.translatable("screen.delvefold.guide.entry_truncated"));
        }
        return List.copyOf(lines);
    }

    private List<FormattedCharSequence> wrappedTooltip(OreEntry ore) {
        int maximumWidth = Math.max(40, Math.min(320, this.width - 24));
        return tooltip(ore).stream()
                .flatMap(line -> this.font.split(line, maximumWidth).stream())
                .toList();
    }

    private Component frequency(OreEntry ore) {
        return Component.translatable("screen.delvefold.guide.frequency."
                + ore.relativeFrequency().name().toLowerCase(Locale.ROOT));
    }

    private Component renewalText(GuideSnapshot.Renewal renewal) {
        if (!renewal.enabled()) {
            return Component.translatable("screen.delvefold.guide.renewal.disabled");
        }
        if (!renewal.scheduled()) {
            return Component.translatable("screen.delvefold.guide.renewal.unscheduled");
        }
        long elapsedSeconds = Math.max(0L, (Util.getMillis() - this.receivedAtMillis) / 1000L);
        long seconds = Math.max(0L, renewal.remainingSeconds() - elapsedSeconds);
        if (renewal.due() || seconds == 0L) {
            return Component.translatable("screen.delvefold.guide.renewal.due");
        }
        if (seconds < 60L) {
            return Component.translatable("screen.delvefold.guide.renewal.less_than_minute");
        }
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        return Component.translatable("screen.delvefold.guide.renewal.remaining", days, hours, minutes);
    }

    private void drawFitted(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        String text = value.getString();
        if (this.font.width(text) > width) {
            String ellipsis = "…";
            text = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width(ellipsis))) + ellipsis;
        }
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private static Component terrainName(String value) {
        if (value == null || value.isBlank() || value.equals("uninitialized")) {
            return Component.translatable("screen.delvefold.guide.uninitialized");
        }
        return Component.translatable("option.delvefold.terrain." + value);
    }

    private static Component terrainVariantName(String value) {
        if (value == null || value.isBlank()) {
            return Component.translatable("screen.delvefold.guide.none");
        }
        return Component.translatable("option.delvefold.terrain_variant." + value);
    }

    private static Component geologyThemeName(String value) {
        if (value == null || value.isBlank()) {
            return Component.translatable("option.delvefold.geology_theme.classic");
        }
        return Component.translatable("option.delvefold.geology_theme." + value);
    }

    private static Component distributionName(String value) {
        return Component.translatable("screen.delvefold.guide.distribution."
                + (value == null ? "uniform" : value.toLowerCase(Locale.ROOT)));
    }

    private static boolean provinceBand(HeightBand band) {
        return band.distribution() != null && band.distribution().startsWith("province_");
    }

    @Override
    public Component getNarrationMessage() {
        return Component.translatable("screen.delvefold.guide.narration",
                this.snapshot.worldName(), terrainName(this.snapshot.terrain()),
                terrainVariantName(this.snapshot.terrainVariant()),
                geologyThemeName(this.snapshot.geologyTheme()), this.snapshot.activeProfile(),
                Component.translatable("screen.delvefold.guide.portal."
                        + this.snapshot.portalStatus().name().toLowerCase(Locale.ROOT)),
                this.snapshot.ores().size());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
