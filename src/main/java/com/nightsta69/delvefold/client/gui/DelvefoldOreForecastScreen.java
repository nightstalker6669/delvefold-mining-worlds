package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.HeightSample;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Read-only, server-authoritative workload and effectiveness forecast. */
public final class DelvefoldOreForecastScreen extends DelvefoldScreen {
    private static final int RULE_SCROLL_STEP = 24;
    private static final int TOOLTIP_ISSUE_LIMIT = 8;
    private final Screen parent;
    private final OreProfileForecast forecast;
    private final double heightGraphMaximum;
    private final ForecastPresentation presentation;
    private OreForecastLayout currentLayout = OreForecastLayout.calculate(new AdminPanelLayout(0, 0, 1, 1), false);
    private int ruleScroll;
    private List<FormattedCharSequence> hoveredTooltip = List.of();
    private List<List<FormattedCharSequence>> wrappedTerrainTooltips = List.of();
    private List<List<FormattedCharSequence>> wrappedRuleTooltips = List.of();
    private List<FormattedCharSequence> wrappedReferenceTooltip = List.of();

    /**
     * Creates a read-only forecast view.
     *
     * @param parent screen restored on close
     * @param adminSnapshot immutable administration context used for navigation and requests
     * @param forecast bounded, redacted server forecast page
     */
    public DelvefoldOreForecastScreen(Screen parent, AdminSnapshot adminSnapshot, OreProfileForecast forecast) {
        super(Component.translatable("screen.delvefold.forecast.title"), adminSnapshot);
        this.parent = parent;
        this.forecast = forecast;
        this.heightGraphMaximum = forecast.activeTerrainHeightOverlay().stream()
                .mapToDouble(HeightSample::expectedWorkUnits)
                .max()
                .orElse(0.0D);
        this.presentation = present(forecast);
    }

    /**
     * Creates a replacement screen for a newly received forecast page.
     *
     * @param replacement newer bounded server forecast
     * @return a new screen retaining the same parent and administration snapshot
     */
    public DelvefoldOreForecastScreen refreshed(OreProfileForecast replacement) {
        return new DelvefoldOreForecastScreen(this.parent, this.snapshot, replacement);
    }

    @Override
    protected int preferredPanelWidth() {
        return 760;
    }

    @Override
    protected int preferredPanelHeight() {
        return 440;
    }

    @Override
    protected void initPanel() {
        this.currentLayout = OreForecastLayout.calculate(
                new AdminPanelLayout(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight),
                this.forecast.truncated());
        clampRuleScroll();
        int tooltipWidth = Math.max(80, Math.min(360, this.width - 24));
        this.wrappedTerrainTooltips = this.presentation.terrains().stream()
                .map(terrain -> wrapTooltip(terrain.tooltip(), tooltipWidth))
                .toList();
        this.wrappedRuleTooltips = this.presentation.rules().stream()
                .map(rule -> wrapTooltip(rule.tooltip(), tooltipWidth))
                .toList();
        this.wrappedReferenceTooltip = wrapTooltip(this.presentation.referenceTooltip(), tooltipWidth);
        int buttonY = this.footerButtonY();
        var back = this.addButton(
                this.contentLeft(),
                buttonY,
                76,
                22,
                Component.translatable("screen.delvefold.back"),
                Style.GHOST,
                button -> this.onClose());
        int pagerWidth = 74;
        int right = this.contentRight();
        var previous = this.addButton(
                right - pagerWidth * 2 - 6,
                buttonY,
                pagerWidth,
                22,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> requestPage(this.forecast.page() - 1));
        previous.active = this.forecast.page() > 0;
        var next = this.addButton(
                right - pagerWidth,
                buttonY,
                pagerWidth,
                22,
                Component.translatable("screen.delvefold.next"),
                Style.PRIMARY,
                button -> requestPage(this.forecast.page() + 1));
        next.active = this.forecast.page() + 1 < this.forecast.pageCount();
        this.setInitialFocus(back);
    }

    private void requestPage(int page) {
        DelvefoldClientRequests.requestForecast(this.forecast.profileId(), page);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int top = ruleViewportTop();
        int bottom = ruleViewportBottom();
        if (mouseY >= top && mouseY < bottom && mouseX >= rulesLeft() && mouseX < this.contentRight()) {
            int maximum = maximumRuleScroll();
            if (maximum > 0) {
                this.ruleScroll =
                        Math.clamp(this.ruleScroll - (int) Math.signum(scrollY) * RULE_SCROLL_STEP, 0, maximum);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = Math.max(RULE_SCROLL_STEP, ruleViewportBottom() - ruleViewportTop() - ruleRowHeight());
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return scrollRulesBy(ruleRowHeight()) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return scrollRulesBy(-ruleRowHeight()) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return scrollRulesBy(page) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            return scrollRulesBy(-page) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            return setRuleScroll(0) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            return setRuleScroll(maximumRuleScroll()) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredTooltip = List.of();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!this.hoveredTooltip.isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        OreForecastLayout layout = this.currentLayout;
        int x = layout.contentLeft();

        renderSummary(graphics, layout, mouseX, mouseY);

        int bodyY = layout.bodyTop();
        int bodyBottom = layout.bodyBottom();
        int graphWidth = layout.graphWidth();
        if (layout.showsHeightGraph()) {
            this.drawCard(graphics, x, bodyY, graphWidth, layout.bodyHeight());
            this.drawSectionTitle(
                    graphics, Component.translatable("screen.delvefold.forecast.height_overlay"), x + 9, bodyY + 7);
            renderHeightGraph(graphics, x + 10, bodyY + 27, graphWidth - 20, bodyBottom - bodyY - 38);
        }

        int rulesX = layout.rulesLeft();
        int rulesWidth = layout.rulesWidth();
        if (layout.bodyHeight() > 0 && rulesWidth > 0) {
            this.drawCard(graphics, rulesX, bodyY, rulesWidth, layout.bodyHeight());
            this.drawSectionTitle(
                    graphics, Component.translatable("screen.delvefold.forecast.rules"), rulesX + 9, bodyY + 7);
            Component page = this.presentation.page();
            graphics.drawString(
                    this.font, page, rulesX + rulesWidth - this.font.width(page) - 9, bodyY + 8, DIM_TEXT, false);
            renderRules(
                    graphics,
                    layout.ruleViewportLeft(),
                    layout.ruleViewportTop(),
                    layout.ruleViewportWidth(),
                    layout.ruleViewportHeight(),
                    mouseX,
                    mouseY);
        }
    }

    private void renderSummary(GuiGraphics graphics, OreForecastLayout layout, int mouseX, int mouseY) {
        int x = layout.contentLeft();
        int y = layout.contentTop();
        int width = layout.contentWidth();
        int height = layout.summaryHeight();
        if (height <= 0 || width <= 0) {
            return;
        }
        this.drawCard(graphics, x, y, width, height);
        if (layout.compactSummary()) {
            if (height >= 18) {
                graphics.drawString(
                        this.font,
                        this.font.plainSubstrByWidth(this.presentation.profile().getString(), Math.max(1, width - 20)),
                        x + 10,
                        y + 8,
                        ACCENT,
                        false);
            }
            if (height >= 35) {
                if (this.forecast.truncated()) {
                    graphics.drawString(
                            this.font,
                            this.font.plainSubstrByWidth(
                                    this.presentation.truncatedWarning().getString(), Math.max(1, width - 20)),
                            x + 10,
                            y + 24,
                            WARNING,
                            false);
                } else {
                    renderReferenceSummary(graphics, x + 10, y + 24, Math.max(1, width - 20), mouseX, mouseY);
                }
            }
            return;
        }
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.forecast.summary"), x + 9, y + 7);
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(this.presentation.profile().getString(), Math.max(1, width - 22)),
                x + 10,
                y + 25,
                ACCENT,
                false);
        renderTerrainTotals(graphics, x + 10, y + 42, Math.max(1, width - 20), mouseX, mouseY);
        renderReferenceSummary(graphics, x + 10, y + 78, Math.max(1, width - 20), mouseX, mouseY);
        if (this.forecast.truncated()) {
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(
                            this.presentation.truncatedWarning().getString(), Math.max(1, width - 20)),
                    x + 10,
                    y + 89,
                    WARNING,
                    false);
        }
    }

    private void renderTerrainTotals(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        List<TerrainPresentation> totals = this.presentation.terrains();
        if (totals.isEmpty()) {
            return;
        }
        int gap = 10;
        int column = Math.max(1, (width - gap * (totals.size() - 1)) / totals.size());
        for (int index = 0; index < totals.size(); index++) {
            TerrainPresentation value = totals.get(index);
            int at = x + index * (column + gap);
            graphics.drawString(this.font, value.label(), at, y, value.labelColor(), false);
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(value.configured().getString(), column),
                    at,
                    y + 11,
                    DIM_TEXT,
                    false);
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(value.effective().getString(), column),
                    at,
                    y + 22,
                    value.effectiveColor(),
                    false);
            if (mouseX >= at && mouseX < at + column && mouseY >= y && mouseY < y + 33) {
                this.hoveredTooltip = this.wrappedTerrainTooltips.get(index);
            }
        }
    }

    private void renderReferenceSummary(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(
                        this.presentation.referenceSummary().getString(), width),
                x,
                y,
                this.presentation.referenceColor(),
                false);
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 10) {
            this.hoveredTooltip = this.wrappedReferenceTooltip;
        }
    }

    private void renderHeightGraph(GuiGraphics graphics, int x, int y, int width, int height) {
        List<HeightSample> samples = this.forecast.activeTerrainHeightOverlay();
        if (samples.isEmpty() || width < 8 || height < 12) {
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.forecast.no_overlay"),
                    x,
                    y,
                    Math.max(1, width),
                    MUTED_TEXT);
            return;
        }
        double maximum = this.heightGraphMaximum;
        if (!(maximum > 0.0D)) {
            maximum = 1.0D;
        }
        int minY = samples.getFirst().y();
        int maxY = samples.getLast().y();
        int graphBottom = y + Math.max(1, height - 13);
        graphics.fill(x, y, x + width, graphBottom, 0x66081014);
        for (int pixel = 0; pixel < width; pixel++) {
            int sampleIndex = Math.min(samples.size() - 1, pixel * samples.size() / width);
            double work = samples.get(sampleIndex).expectedWorkUnits();
            int barHeight = (int) Math.round((graphBottom - y - 1) * work / maximum);
            graphics.fill(
                    x + pixel,
                    graphBottom - barHeight,
                    x + pixel + 1,
                    graphBottom,
                    work > maximum * 0.75D ? ACCENT : ACCENT_DARK);
        }
        graphics.drawString(this.font, Integer.toString(minY), x, graphBottom + 3, DIM_TEXT, false);
        String high = Integer.toString(maxY);
        graphics.drawString(this.font, high, x + width - this.font.width(high), graphBottom + 3, DIM_TEXT, false);
        Component peak = this.presentation.peak();
        graphics.drawString(this.font, peak, x + width - this.font.width(peak), y + 2, MUTED_TEXT, false);
    }

    private void renderRules(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (width <= 0 || height <= 0) {
            return;
        }
        List<RulePresentation> rules = this.presentation.rules();
        if (rules.isEmpty()) {
            graphics.drawWordWrap(
                    this.font, this.presentation.noRules(), x + 3, y + 3, Math.max(1, width - 6), MUTED_TEXT);
            return;
        }
        graphics.enableScissor(x, y, x + width, y + height);
        int rowHeight = ruleRowHeight();
        int rowY = y - this.ruleScroll;
        for (int index = 0; index < rules.size(); index++) {
            RulePresentation row = rules.get(index);
            RuleForecast rule = row.rule();
            if (rowY + rowHeight >= y && rowY < y + height) {
                int statusColor = row.statusColor();
                graphics.fill(x, rowY, x + width, rowY + rowHeight - 3, 0xBB111A20);
                graphics.fill(x, rowY, x + 3, rowY + rowHeight - 3, statusColor);
                graphics.drawString(
                        this.font,
                        this.font.plainSubstrByWidth(rule.ruleId(), Math.max(1, width - 105)),
                        x + 8,
                        rowY + 5,
                        TEXT,
                        false);
                Component status = row.status();
                graphics.drawString(
                        this.font, status, x + width - this.font.width(status) - 6, rowY + 5, statusColor, false);
                graphics.drawString(
                        this.font,
                        this.font.plainSubstrByWidth(row.metrics().getString(), Math.max(1, width - 14)),
                        x + 8,
                        rowY + 16,
                        row.metricColor(),
                        false);
                graphics.drawString(
                        this.font,
                        this.font.plainSubstrByWidth(row.details().getString(), Math.max(1, width - 14)),
                        x + 8,
                        rowY + 28,
                        rule.issues().isEmpty() ? DIM_TEXT : statusColor,
                        false);
                if (mouseX >= x
                        && mouseX < x + width - 3
                        && mouseY >= Math.max(y, rowY)
                        && mouseY < Math.min(y + height, rowY + rowHeight - 3)) {
                    this.hoveredTooltip = this.wrappedRuleTooltips.get(index);
                }
            }
            rowY += rowHeight;
        }
        graphics.disableScissor();
        drawRuleScrollbar(graphics, x + width - 3, y, height);
    }

    private static int statusColor(RuleForecast rule) {
        return switch (rule.status()) {
            case EFFECTIVE -> SUCCESS;
            case DISABLED, UNINITIALIZED, TERRAIN_MISMATCH, BIOME_MISMATCH, NO_ATTEMPTS -> MUTED_TEXT;
            case NO_EFFECTIVE_OUTPUTS, INVALID -> rule.required() ? DANGER : WARNING;
        };
    }

    private int maximumRuleScroll() {
        return Math.max(
                0,
                this.forecast.rules().size() * ruleRowHeight() - Math.max(1, this.currentLayout.ruleViewportHeight()));
    }

    private void drawRuleScrollbar(GuiGraphics graphics, int x, int y, int height) {
        int maximum = maximumRuleScroll();
        if (maximum <= 0) {
            return;
        }
        int contentHeight = this.forecast.rules().size() * ruleRowHeight();
        int thumb = Math.min(height, Math.max(12, height * height / Math.max(height, contentHeight)));
        int travel = Math.max(1, height - thumb);
        int thumbY = y + this.ruleScroll * travel / maximum;
        graphics.fill(x, y, x + 2, y + height, 0xAA081014);
        graphics.fill(x, thumbY, x + 2, thumbY + thumb, ACCENT);
    }

    private int rulesLeft() {
        return this.currentLayout.rulesLeft();
    }

    private int ruleViewportTop() {
        return this.currentLayout.ruleViewportTop();
    }

    private int ruleViewportBottom() {
        return this.currentLayout.ruleViewportBottom();
    }

    private int ruleRowHeight() {
        return this.currentLayout.ruleRowHeight();
    }

    private boolean scrollRulesBy(int amount) {
        return setRuleScroll(this.ruleScroll + amount);
    }

    private boolean setRuleScroll(int requested) {
        int previous = this.ruleScroll;
        this.ruleScroll = Math.clamp(requested, 0, maximumRuleScroll());
        return this.ruleScroll != previous;
    }

    private void clampRuleScroll() {
        this.ruleScroll = Math.clamp(this.ruleScroll, 0, maximumRuleScroll());
    }

    private static int effectivenessColor(double configured, double effective) {
        if (effective <= 0.0D && configured > 0.0D) {
            return WARNING;
        }
        return effective + 0.005D < configured ? MUTED_TEXT : DIM_TEXT;
    }

    private static Component configuredMetric(double attempts, double workUnits) {
        return Component.translatable(
                "screen.delvefold.forecast.metric.configured", format(attempts), format(workUnits));
    }

    private static Component effectiveMetric(double attempts, double workUnits) {
        return Component.translatable(
                "screen.delvefold.forecast.metric.effective", format(attempts), format(workUnits));
    }

    private static Component ruleMetric(RuleForecast rule) {
        return Component.translatable(
                "screen.delvefold.forecast.metric.comparison",
                format(rule.configuredAttempts()),
                format(rule.configuredWorkUnits()),
                format(rule.effectiveAttempts()),
                format(rule.effectiveWorkUnits()));
    }

    private static Component ruleDetails(RuleForecast rule) {
        if (rule.issues().isEmpty()) {
            return Component.translatable(
                    "screen.delvefold.forecast.rule.counts",
                    rule.targetCount(),
                    rule.effectiveOutputCount(),
                    rule.missingReferenceCount(),
                    rule.shadowedOutputCount());
        }
        ReferenceIssue issue = rule.issues().getFirst();
        return Component.translatable(
                "screen.delvefold.forecast.rule.counts_with_issue",
                rule.targetCount(),
                rule.effectiveOutputCount(),
                rule.missingReferenceCount(),
                rule.shadowedOutputCount(),
                issueKind(issue),
                displayIdentifier(issue.referenceId()));
    }

    private static Component referenceSummary(ReferenceSummary references) {
        return Component.translatable(
                "screen.delvefold.forecast.references.summary",
                references.totalIssues(),
                references.missingBlocks(),
                references.missingOutputTags(),
                references.missingHostTags(),
                references.invalidStates(),
                references.shadowedOutputs());
    }

    private List<Component> ruleTooltip(RuleForecast rule) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(rule.ruleId()));
        lines.add(Component.translatable(
                "screen.delvefold.forecast.tooltip.status",
                Component.translatable("screen.delvefold.forecast.status."
                        + rule.status().name().toLowerCase(Locale.ROOT))));
        lines.add(configuredMetric(rule.configuredAttempts(), rule.configuredWorkUnits()));
        lines.add(effectiveMetric(rule.effectiveAttempts(), rule.effectiveWorkUnits()));
        lines.add(Component.translatable(
                "screen.delvefold.forecast.rule.counts",
                rule.targetCount(),
                rule.effectiveOutputCount(),
                rule.missingReferenceCount(),
                rule.shadowedOutputCount()));
        appendIssues(lines, rule.issues());
        if (rule.truncated()) {
            lines.add(Component.translatable("screen.delvefold.forecast.rule.truncated"));
        }
        return List.copyOf(lines);
    }

    private List<Component> referenceTooltip(ReferenceSummary references) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.delvefold.forecast.references.title"));
        lines.add(referenceSummary(references));
        if (references.details().isEmpty()) {
            lines.add(Component.translatable("screen.delvefold.forecast.references.none"));
        } else {
            appendIssues(lines, references.details());
        }
        if (references.truncated()) {
            lines.add(Component.translatable("screen.delvefold.forecast.references.truncated"));
        }
        if (this.forecast.truncated()) {
            lines.add(Component.translatable("screen.delvefold.forecast.truncated"));
        }
        return List.copyOf(lines);
    }

    private void appendIssues(List<Component> lines, List<ReferenceIssue> issues) {
        int shown = Math.min(TOOLTIP_ISSUE_LIMIT, issues.size());
        for (int index = 0; index < shown; index++) {
            ReferenceIssue issue = issues.get(index);
            lines.add(Component.translatable(
                    "screen.delvefold.forecast.issue.detail",
                    issueSeverity(issue),
                    issueKind(issue),
                    displayIdentifier(issue.sourceId()),
                    displayIdentifier(issue.referenceId()),
                    issue.affectedOutputs()));
            lines.add(Component.translatable(
                    "screen.delvefold.forecast.issue.location",
                    issue.ruleId(),
                    issue.targetIndex() < 0
                            ? Component.translatable("screen.delvefold.forecast.issue.target.general")
                            : Integer.toString(issue.targetIndex() + 1)));
        }
        if (issues.size() > shown) {
            lines.add(Component.translatable("screen.delvefold.forecast.issue.more", issues.size() - shown));
        }
    }

    private static Component issueKind(ReferenceIssue issue) {
        return Component.translatable(
                "screen.delvefold.forecast.issue.kind." + issue.kind().name().toLowerCase(Locale.ROOT));
    }

    private static Component issueSeverity(ReferenceIssue issue) {
        return Component.translatable("screen.delvefold.forecast.issue.severity."
                + issue.severity().name().toLowerCase(Locale.ROOT));
    }

    private static Component displayIdentifier(String value) {
        return value == null || value.isBlank()
                ? Component.translatable("screen.delvefold.forecast.issue.none")
                : Component.literal(value);
    }

    private List<FormattedCharSequence> wrapTooltip(List<Component> lines, int maximumWidth) {
        return lines.stream()
                .flatMap(line -> this.font.split(line, maximumWidth).stream())
                .toList();
    }

    @Override
    public Component getNarrationMessage() {
        return this.presentation.narration();
    }

    /* Converts immutable server forecast data into resize-invariant components and tooltip sources once. */
    private ForecastPresentation present(OreProfileForecast source) {
        List<TerrainPresentation> terrains = source.terrainTotals().stream()
                .map(value -> {
                    Component terrainLabel = Component.translatable(
                            "option.delvefold.terrain." + value.terrain().serializedName());
                    Component label = value.active()
                            ? Component.translatable("screen.delvefold.forecast.terrain.active_label", terrainLabel)
                            : terrainLabel;
                    Component configured = configuredMetric(value.configuredAttempts(), value.configuredWorkUnits());
                    Component effective = effectiveMetric(value.effectiveAttempts(), value.effectiveWorkUnits());
                    return new TerrainPresentation(
                            label,
                            configured,
                            effective,
                            value.active() ? ACCENT : MUTED_TEXT,
                            effectivenessColor(value.configuredWorkUnits(), value.effectiveWorkUnits()),
                            List.of(label, configured, effective));
                })
                .toList();
        List<RulePresentation> rules = source.rules().stream()
                .map(rule -> new RulePresentation(
                        rule,
                        Component.translatable("screen.delvefold.forecast.status."
                                + rule.status().name().toLowerCase(Locale.ROOT)),
                        ruleMetric(rule),
                        ruleDetails(rule),
                        statusColor(rule),
                        effectivenessColor(rule.configuredWorkUnits(), rule.effectiveWorkUnits()),
                        ruleTooltip(rule)))
                .toList();
        ReferenceSummary references = source.references();
        return new ForecastPresentation(
                Component.translatable(
                        "screen.delvefold.forecast.profile", source.profileId(), source.totalRuleCount()),
                Component.translatable("screen.delvefold.forecast.page", source.page() + 1, source.pageCount()),
                Component.translatable(
                        "screen.delvefold.forecast.peak",
                        format(this.heightGraphMaximum > 0.0D ? this.heightGraphMaximum : 1.0D)),
                referenceSummary(references),
                references.totalIssues() == 0 ? SUCCESS : WARNING,
                referenceTooltip(references),
                Component.translatable("screen.delvefold.forecast.truncated"),
                Component.translatable("screen.delvefold.forecast.no_rules"),
                terrains,
                rules,
                Component.translatable(
                        "screen.delvefold.forecast.narration",
                        source.profileId(),
                        source.page() + 1,
                        source.pageCount(),
                        source.totalRuleCount(),
                        references.totalIssues()));
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /* Immutable preformatted terrain summary and hover source. */
    private record TerrainPresentation(
            Component label,
            Component configured,
            Component effective,
            int labelColor,
            int effectiveColor,
            List<Component> tooltip) {
        private TerrainPresentation {
            tooltip = List.copyOf(tooltip);
        }
    }

    /* Immutable preformatted rule row and hover source. */
    private record RulePresentation(
            RuleForecast rule,
            Component status,
            Component metrics,
            Component details,
            int statusColor,
            int metricColor,
            List<Component> tooltip) {
        private RulePresentation {
            tooltip = List.copyOf(tooltip);
        }
    }

    /* Immutable presentation snapshot shared by all render passes at one server revision. */
    private record ForecastPresentation(
            Component profile,
            Component page,
            Component peak,
            Component referenceSummary,
            int referenceColor,
            List<Component> referenceTooltip,
            Component truncatedWarning,
            Component noRules,
            List<TerrainPresentation> terrains,
            List<RulePresentation> rules,
            Component narration) {
        private ForecastPresentation {
            referenceTooltip = List.copyOf(referenceTooltip);
            terrains = List.copyOf(terrains);
            rules = List.copyOf(rules);
        }
    }
}
