package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetAnalysis;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetIssue;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Server-registry adapter for the pure ore-profile forecast builder. */
public final class MinecraftOreProfileForecastBuilder {
    private MinecraftOreProfileForecastBuilder() {}

    public static OreProfileForecast build(ConfigSnapshot source, RegistryAccess registries, int page, int pageSize) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(source.settings(), "source.settings");
        Objects.requireNonNull(source.ores(), "source.ores");
        return build(
                source.settings().activeProfileId(),
                source.ores(),
                source.settings().terrainMode(),
                registries,
                page,
                pageSize);
    }

    public static OreProfileForecast build(
            String profileId,
            OreProfileDocument profile,
            TerrainMode activeTerrain,
            RegistryAccess registries,
            int page,
            int pageSize) {
        Objects.requireNonNull(registries, "registries");
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        return OreProfileForecastBuilder.build(
                profileId,
                profile,
                activeTerrain,
                (terrain, filter) -> biomes.getHolder(miningBiome(terrain))
                        .map(holder -> OreBiomeMatcher.matches(filter, holder))
                        .orElse(false),
                MinecraftOreProfileForecastBuilder::analyzeTargets,
                page,
                pageSize);
    }

    public static OreProfileForecast build(
            OreProfileDocument profile, TerrainMode activeTerrain, RegistryAccess registries, int page, int pageSize) {
        Objects.requireNonNull(profile, "profile");
        return build(profile.profile(), profile, activeTerrain, registries, page, pageSize);
    }

    private static TargetAnalysis analyzeTargets(com.nightsta69.delvefold.config.model.OreRule rule) {
        OreTargetResolution.Result resolution = OreTargetResolution.resolve(rule);
        return new TargetAnalysis(
                resolution.effectiveOutputCount(),
                resolution.shadowedOutputCount(),
                resolution.issues().stream()
                        .map(issue -> new TargetIssue(
                                IssueKind.valueOf(issue.kind().name()),
                                issue.severity() == com.nightsta69.delvefold.config.validation.IssueSeverity.ERROR
                                        ? IssueSeverity.ERROR
                                        : IssueSeverity.WARNING,
                                issue.targetIndex(),
                                issue.sourceId(),
                                issue.referenceId(),
                                issue.affectedOutputs()))
                        .toList(),
                resolution.issuesTruncated());
    }

    private static ResourceKey<Biome> miningBiome(TerrainMode terrain) {
        return switch (terrain) {
            case FLAT -> DelvefoldWorldgen.MINING_FLAT_BIOME;
            case CAVERN -> DelvefoldWorldgen.MINING_CAVERN_BIOME;
            case WILD -> DelvefoldWorldgen.MINING_WILD_BIOME;
        };
    }
}
