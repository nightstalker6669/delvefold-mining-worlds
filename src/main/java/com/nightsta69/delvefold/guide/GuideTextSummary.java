package com.nightsta69.delvefold.guide;

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
        lines.add("Delvefold Seam Ledger — " + snapshot.worldName());
        lines.add("Terrain: " + snapshot.terrain() + " / " + snapshot.terrainVariant()
                + "; geology: " + snapshot.geologyTheme() + "; profile: " + snapshot.activeProfile());
        lines.add("Portal: " + readable(snapshot.portalStatus().name())
                + "; renewal: " + renewal(snapshot.renewal()));
        lines.add("Published ores: " + snapshot.ores().size() + (snapshot.truncated() ? " (truncated)" : ""));

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
                biomes += (biomes.isBlank() ? "" : "; ") + "excluding "
                        + String.join(", ", ore.applicability().biomeExcludes());
            }
            lines.add("- " + ore.ruleId() + ": " + outputs + "; best "
                    + (heights.isBlank() ? "height unavailable" : heights)
                    + "; biomes " + (biomes.isBlank() ? "all" : biomes)
                    + "; " + readable(ore.relativeFrequency().name()));
        });
        if (snapshot.ores().size() > MAX_CONSOLE_ORES) {
            lines.add("- … " + (snapshot.ores().size() - MAX_CONSOLE_ORES) + " additional entries");
        }
        return List.copyOf(lines);
    }

    private static String renewal(GuideSnapshot.Renewal renewal) {
        if (!renewal.enabled()) {
            return "disabled";
        }
        if (!renewal.scheduled()) {
            return "enabled, not yet scheduled";
        }
        if (renewal.due()) {
            return "due";
        }
        long seconds = renewal.remainingSeconds();
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    private static String readable(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
