package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.analysis.OreWorkBudgetAnalysis;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.guide.GuideSnapshot.Applicability;
import com.nightsta69.delvefold.guide.GuideSnapshot.HeightBand;
import com.nightsta69.delvefold.guide.GuideSnapshot.OreEntry;
import com.nightsta69.delvefold.guide.GuideSnapshot.Output;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import com.nightsta69.delvefold.guide.GuideSnapshot.PortalStatus;
import com.nightsta69.delvefold.guide.GuideSnapshot.RelativeFrequency;
import com.nightsta69.delvefold.guide.GuideSnapshot.Renewal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Converts the immutable configuration snapshot into the deliberately narrow guide contract. */
public final class GuideSnapshotBuilder {
    private GuideSnapshotBuilder() {
    }

    public static GuideSnapshot build(ConfigSnapshot source, long nowEpochMillis, boolean portalEntryBlocked) {
        return build(source, nowEpochMillis, portalEntryBlocked, MinecraftGuideIconResolver.INSTANCE);
    }

    public static GuideSnapshot build(
            ConfigSnapshot source,
            long nowEpochMillis,
            boolean portalEntryBlocked,
            GuideIconResolver iconResolver) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(source.settings(), "source.settings");
        Objects.requireNonNull(source.ores(), "source.ores");
        Objects.requireNonNull(iconResolver, "iconResolver");

        WorldSettingsDocument settings = source.settings();
        String worldName = GuideLimits.boundedText(
                settings.identity().displayName(), GuideLimits.MAX_WORLD_NAME_CHARACTERS);
        String terrain = settings.terrainMode() == null ? "uninitialized" : settings.terrainMode().serializedName();
        String terrainVariant = settings.identity().terrainVariant().serializedName();
        String geologyTheme = settings.identity().geologyTheme().serializedName();
        String activeProfile = GuideLimits.boundedText(
                settings.activeProfileId(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        PortalStatus portalStatus = portalStatus(settings, portalEntryBlocked);
        Renewal renewal = renewal(settings.identity().renewal(), nowEpochMillis);

        List<OreRule> enabledRules = source.ores().rules().stream().filter(OreRule::enabled).toList();
        double maximumWork = enabledRules.stream()
                .mapToDouble(rule -> activeWork(rule, settings.terrainMode()))
                .max()
                .orElse(0.0D);
        int estimatedBytes = GuideSnapshot.estimatedBaseNetworkBytes(
                worldName, terrain, terrainVariant, geologyTheme, activeProfile, renewal);
        boolean truncated = false;
        List<OreEntry> entries = new ArrayList<>();

        for (OreRule rule : enabledRules) {
            if (entries.size() >= GuideLimits.MAX_ORE_ENTRIES) {
                truncated = true;
                break;
            }
            OreEntry entry = oreEntry(rule, settings.terrainMode(), maximumWork, iconResolver);
            if (estimatedBytes + entry.estimatedNetworkBytes() > GuideLimits.MAX_ESTIMATED_NETWORK_BYTES) {
                truncated = true;
                break;
            }
            entries.add(entry);
            estimatedBytes += entry.estimatedNetworkBytes();
            truncated |= entry.truncated();
        }

        return new GuideSnapshot(
                GuideSnapshot.CURRENT_FORMAT_VERSION,
                worldName,
                terrain,
                terrainVariant,
                geologyTheme,
                activeProfile,
                portalStatus,
                renewal,
                entries,
                truncated);
    }

    private static PortalStatus portalStatus(WorldSettingsDocument settings, boolean entryBlocked) {
        if (!settings.initialized()) {
            return PortalStatus.UNINITIALIZED;
        }
        if (!settings.portal().enabled()) {
            return PortalStatus.DISABLED;
        }
        return entryBlocked ? PortalStatus.ENTRY_BLOCKED : PortalStatus.AVAILABLE;
    }

    private static Renewal renewal(RenewalSettings settings, long nowEpochMillis) {
        boolean scheduled = settings.enabled() && settings.nextRenewalAtEpochMillis() > 0L;
        if (!scheduled) {
            return new Renewal(settings.enabled(), false, false, 0L);
        }
        long remainingMillis;
        try {
            remainingMillis = Math.subtractExact(settings.nextRenewalAtEpochMillis(), nowEpochMillis);
        } catch (ArithmeticException ignored) {
            remainingMillis = settings.nextRenewalAtEpochMillis() > nowEpochMillis ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        boolean due = remainingMillis <= 0L;
        long seconds = due ? 0L : 1L + (remainingMillis - 1L) / 1000L;
        return new Renewal(true, true, due, seconds);
    }

    private static OreEntry oreEntry(
            OreRule rule,
            TerrainMode activeTerrain,
            double maximumWork,
            GuideIconResolver iconResolver) {
        boolean truncated = rule.targets().size() > GuideLimits.MAX_OUTPUTS_PER_ENTRY
                || rule.bands().size() > GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY
                || rule.biomes().include().size() > GuideLimits.MAX_BIOME_SELECTORS_PER_LIST
                || rule.biomes().exclude().size() > GuideLimits.MAX_BIOME_SELECTORS_PER_LIST;
        List<Output> outputs = rule.targets().stream()
                .limit(GuideLimits.MAX_OUTPUTS_PER_ENTRY)
                .map(target -> output(target, iconResolver))
                .toList();
        List<String> terrains = rule.terrainModes().stream()
                .sorted()
                .map(TerrainMode::serializedName)
                .toList();
        boolean applies = activeTerrain != null && rule.terrainModes().contains(activeTerrain);
        Applicability applicability = new Applicability(
                terrains,
                applies,
                customBiomeFilter(rule.biomes()),
                rule.biomes().include().stream().limit(GuideLimits.MAX_BIOME_SELECTORS_PER_LIST).toList(),
                rule.biomes().exclude().stream().limit(GuideLimits.MAX_BIOME_SELECTORS_PER_LIST).toList());
        List<HeightBand> bands = rule.bands().stream()
                .filter(GuideSnapshotBuilder::validBand)
                .limit(GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY)
                .map(GuideSnapshotBuilder::heightBand)
                .toList();
        if (bands.size() < Math.min(rule.bands().size(), GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY)) {
            truncated = true;
        }
        RelativeFrequency frequency = applies
                ? relativeFrequency(activeWork(rule, activeTerrain), maximumWork)
                : RelativeFrequency.NOT_APPLICABLE;
        return new OreEntry(rule.id(), outputs, applicability, bands, frequency, truncated);
    }

    private static Output output(OreTarget target, GuideIconResolver resolver) {
        OutputKind kind = target.tagDriven() ? OutputKind.BLOCK_TAG : OutputKind.BLOCK;
        String sourceId = target.tagDriven() ? target.blockTag() : target.block();
        String icon = resolver.representativeBlock(kind, sourceId).orElse("");
        return new Output(kind, sourceId, icon);
    }

    private static boolean customBiomeFilter(BiomeFilter filter) {
        if (filter == null || filter.include().isEmpty() && filter.exclude().isEmpty()) {
            return false;
        }
        return !BiomeFilter.ALL_MINING_BIOMES.equals(filter);
    }

    private static boolean validBand(SpawnBand band) {
        return band.minY() <= band.maxY()
                && (band.placement() == OreBandPlacement.PROVINCE
                        || band.veinSize() >= 1 && band.veinSize() <= 64);
    }

    private static HeightBand heightBand(SpawnBand band) {
        int bestMin = band.minY();
        int bestMax = band.maxY();
        if (band.distribution() == HeightDistribution.TRIANGLE) {
            int peak = band.peakY() == null ? (band.minY() + band.maxY()) / 2 : band.peakY();
            bestMin = Math.clamp(peak, band.minY(), band.maxY());
            bestMax = bestMin;
        } else if (band.distribution() == HeightDistribution.TRAPEZOID) {
            int plateauMin = band.plateauMinY() == null ? band.minY() : band.plateauMinY();
            int plateauMax = band.plateauMaxY() == null ? band.maxY() : band.plateauMaxY();
            bestMin = Math.clamp(plateauMin, band.minY(), band.maxY());
            bestMax = Math.clamp(plateauMax, bestMin, band.maxY());
        }
        String distribution = band.distribution().name().toLowerCase(java.util.Locale.ROOT);
        if (band.placement() == OreBandPlacement.PROVINCE) {
            distribution = "province_" + distribution;
        }
        return new HeightBand(
                band.id(), distribution,
                band.minY(), band.maxY(), bestMin, bestMax,
                band.placement() == OreBandPlacement.PROVINCE ? 1 : band.veinSize());
    }

    private static double activeWork(OreRule rule, TerrainMode activeTerrain) {
        if (activeTerrain == null || !rule.terrainModes().contains(activeTerrain)) {
            return 0.0D;
        }
        return OreWorkBudgetAnalysis.analyze(rule, activeTerrain).workUnitsPerChunk();
    }

    private static RelativeFrequency relativeFrequency(double work, double maximumWork) {
        if (!(work > 0.0D) || !(maximumWork > 0.0D)) {
            return RelativeFrequency.NONE;
        }
        double ratio = work / maximumWork;
        if (ratio <= 0.10D) return RelativeFrequency.VERY_RARE;
        if (ratio <= 0.25D) return RelativeFrequency.RARE;
        if (ratio <= 0.50D) return RelativeFrequency.UNCOMMON;
        if (ratio <= 0.75D) return RelativeFrequency.COMMON;
        return RelativeFrequency.ABUNDANT;
    }
}
