package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Bounded text rendering used when /delvefold guide is run from the server console. */
public final class GuideTextSummary {
    private static final int MAX_CONSOLE_ORES = 24;

    private GuideTextSummary() {
    }

    public static List<String> lines(GuideSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(localized("message.delvefold.guide.console.title", snapshot.worldName()));
        lines.add(localized("message.delvefold.guide.console.identity", snapshot.terrain(),
                snapshot.terrainVariant(), snapshot.geologyTheme(), snapshot.activeProfile()));
        lines.add(localized("screen.delvefold.guide.status_line",
                localized("screen.delvefold.guide.portal."
                        + snapshot.portalStatus().name().toLowerCase(Locale.ROOT)),
                renewal(snapshot.renewal())));
        lines.add(localized("screen.delvefold.guide.ore_count", snapshot.ores().size()));
        if (snapshot.truncated()) {
            lines.add(localized("screen.delvefold.guide.truncated"));
        }

        snapshot.ores().stream().limit(MAX_CONSOLE_ORES).forEach(ore -> {
            String outputs = ore.outputs().stream()
                    .map(output -> output.kind() == GuideSnapshot.OutputKind.BLOCK_TAG
                            ? "#" + output.sourceId() : output.sourceId())
                    .collect(Collectors.joining(", "));
            String heights = ore.heightBands().stream()
                    .map(band -> band.bestMinY() == band.bestMaxY()
                            ? "Y " + band.bestMinY()
                            : "Y " + band.bestMinY() + ".." + band.bestMaxY())
                    .collect(Collectors.joining(", "));
            String biomes = ore.applicability().biomeIncludes().stream().collect(Collectors.joining(", "));
            if (!ore.applicability().biomeExcludes().isEmpty()) {
                biomes = localized("screen.delvefold.guide.biomes_excluding",
                        biomes.isBlank()
                                ? localized("screen.delvefold.guide.all_mining_biomes")
                                : biomes,
                        String.join(", ", ore.applicability().biomeExcludes()));
            }
            lines.add(localized("message.delvefold.guide.console.ore", ore.ruleId(), outputs,
                    heights.isBlank()
                            ? localized("message.delvefold.guide.console.height_unavailable")
                            : heights,
                    biomes.isBlank()
                            ? localized("screen.delvefold.guide.all_mining_biomes")
                            : biomes,
                    localized("screen.delvefold.guide.frequency."
                            + ore.relativeFrequency().name().toLowerCase(Locale.ROOT))));
        });
        if (snapshot.ores().size() > MAX_CONSOLE_ORES) {
            lines.add(localized("message.delvefold.guide.console.additional_entries",
                    snapshot.ores().size() - MAX_CONSOLE_ORES));
        }
        return List.copyOf(lines);
    }

    private static String renewal(GuideSnapshot.Renewal renewal) {
        if (!renewal.enabled()) {
            return localized("screen.delvefold.guide.renewal.disabled");
        }
        if (!renewal.scheduled()) {
            return localized("screen.delvefold.guide.renewal.unscheduled");
        }
        if (renewal.due()) {
            return localized("screen.delvefold.guide.renewal.due");
        }
        long seconds = renewal.remainingSeconds();
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        if (seconds < 60L) {
            return localized("screen.delvefold.guide.renewal.less_than_minute");
        }
        return localized("screen.delvefold.guide.renewal.remaining",
                days, hours, Math.max(0L, minutes));
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
