package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * One validated server-datapack landmark definition.
 *
 * @param id catalog ID derived from the definition resource path
 * @param template structure-template ID, without the {@code structure/} resource prefix or {@code .nbt} suffix
 * @param weight weighted-selection value from 1 through 1000
 * @param category existing world-identity toggle that may disable this definition
 * @param terrainModes nonempty set of compatible terrain modes
 * @param placementStyle rule used to resolve the template origin's block Y coordinate
 * @param minY inclusive minimum origin height in blocks
 * @param maxY inclusive maximum origin height in blocks
 * @param biomes exact biome IDs and biome-tag selectors applied at the resolved origin
 * @param processors processor lists applied in declared order after the structure-block ignore processor
 * @param lootTable loot table assigned once to containers beneath {@code loot} data markers
 */
public record LandmarkDefinition(
        ResourceLocation id,
        ResourceLocation template,
        int weight,
        LandmarkCategory category,
        Set<TerrainMode> terrainModes,
        LandmarkPlacementStyle placementStyle,
        int minY,
        int maxY,
        LandmarkBiomeSelectors biomes,
        List<ResourceKey<StructureProcessorList>> processors,
        ResourceKey<LootTable> lootTable) {

    /** Maximum accepted template width or depth in blocks. */
    public static final int MAX_TEMPLATE_HORIZONTAL_SPAN = 96;
    /** Maximum accepted template height in blocks. */
    public static final int MAX_TEMPLATE_VERTICAL_SPAN = 384;

    private static final Codec<TerrainMode> TERRAIN_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(TerrainMode.parse(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(exception::getMessage);
                }
            },
            TerrainMode::serializedName);

    private static final MapCodec<IdentityFields> IDENTITY_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            Codec.intRange(1, 1).fieldOf("format").forGetter(IdentityFields::format),
                            ResourceLocation.CODEC.fieldOf("template").forGetter(IdentityFields::template),
                            Codec.intRange(1, 1000).fieldOf("weight").forGetter(IdentityFields::weight),
                            LandmarkCategory.CODEC.fieldOf("category").forGetter(IdentityFields::category),
                            TERRAIN_CODEC.listOf().fieldOf("terrain_modes").forGetter(IdentityFields::terrainModes))
                    .apply(instance, IdentityFields::new));

    private static final MapCodec<PlacementFields> PLACEMENT_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            LandmarkPlacementStyle.CODEC
                                    .fieldOf("placement_style")
                                    .forGetter(PlacementFields::placementStyle),
                            Codec.intRange(-2048, 2047).fieldOf("min_y").forGetter(PlacementFields::minY),
                            Codec.intRange(-2048, 2047).fieldOf("max_y").forGetter(PlacementFields::maxY),
                            LandmarkBiomeSelectors.CODEC
                                    .optionalFieldOf("biomes", LandmarkBiomeSelectors.miningBiomes())
                                    .forGetter(PlacementFields::biomes))
                    .apply(instance, PlacementFields::new));

    private static final MapCodec<ResourceFields> RESOURCE_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            ResourceKey.codec(Registries.PROCESSOR_LIST)
                                    .listOf()
                                    .optionalFieldOf("processors", List.of())
                                    .forGetter(ResourceFields::processors),
                            ResourceKey.codec(Registries.LOOT_TABLE)
                                    .fieldOf("loot_table")
                                    .forGetter(ResourceFields::lootTable))
                    .apply(instance, ResourceFields::new));

    /**
     * Codec for the JSON body beneath {@code data/<namespace>/delvefold/landmarks/}.
     *
     * <p>The resource ID is supplied separately by the reload listener. Decoding is performed during the platform's
     * reload preparation stage before an atomic catalog snapshot is published.
     */
    public static final Codec<Body> BODY_CODEC = RecordCodecBuilder.<Body>create(instance -> instance.group(
                            IDENTITY_CODEC.forGetter(Body::identityFields),
                            PLACEMENT_CODEC.forGetter(Body::placementFields),
                            RESOURCE_CODEC.forGetter(Body::resourceFields))
                    .apply(instance, Body::new))
            .validate(Body::validate);

    /**
     * Normalizes immutable collections and enforces runtime construction invariants.
     *
     * @param id catalog definition ID
     * @param template structure-template ID
     * @param weight weighted-selection value from 1 through 1000
     * @param category world-identity toggle category
     * @param terrainModes nonempty compatible terrain set
     * @param placementStyle terrain anchoring rule
     * @param minY inclusive minimum origin block Y
     * @param maxY inclusive maximum origin block Y
     * @param biomes biome selectors, defaulting to Delvefold mining biomes when absent
     * @param processors ordered processor-list keys
     * @param lootTable marker-container loot table
     * @throws NullPointerException if a required identifier, category, style, or loot table is null
     * @throws IllegalArgumentException if weight, height ordering, or terrain-mode invariants are invalid
     */
    public LandmarkDefinition {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(template, "template");
        java.util.Objects.requireNonNull(category, "category");
        terrainModes = terrainModes == null || terrainModes.isEmpty() ? Set.of() : Set.copyOf(terrainModes);
        java.util.Objects.requireNonNull(placementStyle, "placementStyle");
        biomes = biomes == null ? LandmarkBiomeSelectors.miningBiomes() : biomes;
        processors = processors == null ? List.of() : List.copyOf(processors);
        java.util.Objects.requireNonNull(lootTable, "lootTable");
        if (weight < 1 || weight > 1000 || minY > maxY || terrainModes.isEmpty()) {
            throw new IllegalArgumentException("Invalid landmark definition " + id);
        }
    }

    /**
     * Combines a resource-derived ID with a decoded and validated body.
     *
     * @param id catalog ID derived from the JSON resource path
     * @param body codec-validated resource body
     * @return immutable runtime definition
     * @throws IllegalArgumentException if runtime construction invariants are violated
     */
    public static LandmarkDefinition from(ResourceLocation id, Body body) {
        return new LandmarkDefinition(
                id,
                body.template(),
                body.weight(),
                body.category(),
                EnumSet.copyOf(body.terrainModes()),
                body.placementStyle(),
                body.minY(),
                body.maxY(),
                body.biomes(),
                body.processors(),
                body.lootTable());
    }

    /**
     * Codec-facing landmark document before its resource-path ID is attached.
     *
     * @param format landmark document format; currently exactly {@code 1}
     * @param template structure-template ID
     * @param weight weighted-selection value from 1 through 1000
     * @param category world-identity toggle category
     * @param terrainModes nonempty, duplicate-free list of compatible terrain modes
     * @param placementStyle terrain anchoring rule
     * @param minY inclusive minimum origin block Y, from -2048 through 2047
     * @param maxY inclusive maximum origin block Y, from -2048 through 2047
     * @param biomes bounded exact-ID and tag selectors
     * @param processors ordered processor-list keys
     * @param lootTable loot table for marker containers
     */
    public record Body(
            int format,
            ResourceLocation template,
            int weight,
            LandmarkCategory category,
            List<TerrainMode> terrainModes,
            LandmarkPlacementStyle placementStyle,
            int minY,
            int maxY,
            LandmarkBiomeSelectors biomes,
            List<ResourceKey<StructureProcessorList>> processors,
            ResourceKey<LootTable> lootTable) {
        private Body(IdentityFields identity, PlacementFields placement, ResourceFields resources) {
            this(
                    identity.format(),
                    identity.template(),
                    identity.weight(),
                    identity.category(),
                    identity.terrainModes(),
                    placement.placementStyle(),
                    placement.minY(),
                    placement.maxY(),
                    placement.biomes(),
                    resources.processors(),
                    resources.lootTable());
        }

        private IdentityFields identityFields() {
            return new IdentityFields(format, template, weight, category, terrainModes);
        }

        private PlacementFields placementFields() {
            return new PlacementFields(placementStyle, minY, maxY, biomes);
        }

        private ResourceFields resourceFields() {
            return new ResourceFields(processors, lootTable);
        }

        private static DataResult<Body> validate(Body body) {
            if (body.terrainModes() == null || body.terrainModes().isEmpty()) {
                return DataResult.error(() -> "terrain_modes must contain at least one mode");
            }
            if (body.terrainModes().stream().distinct().count()
                    != body.terrainModes().size()) {
                return DataResult.error(() -> "terrain_modes contains duplicates");
            }
            if (body.minY() > body.maxY()) {
                return DataResult.error(() -> "min_y must not exceed max_y");
            }
            String selectorError = validateSelectors(body.biomes());
            return selectorError == null ? DataResult.success(body) : DataResult.error(() -> selectorError);
        }

        private static @Nullable String validateSelectors(LandmarkBiomeSelectors selectors) {
            if (selectors.include().size() > 32 || selectors.exclude().size() > 32) {
                return "biome selector lists may contain at most 32 entries";
            }
            for (String selector : java.util.stream.Stream.concat(
                            selectors.include().stream(), selectors.exclude().stream())
                    .toList()) {
                if (selector == null || selector.isBlank()) {
                    return "biome selectors cannot be blank";
                }
                String value = selector.startsWith("#") ? selector.substring(1) : selector;
                if (ResourceLocation.tryParse(value) == null) {
                    return "invalid biome selector: " + selector;
                }
            }
            return null;
        }
    }

    private record IdentityFields(
            int format,
            ResourceLocation template,
            int weight,
            LandmarkCategory category,
            List<TerrainMode> terrainModes) {}

    private record PlacementFields(
            LandmarkPlacementStyle placementStyle, int minY, int maxY, LandmarkBiomeSelectors biomes) {}

    private record ResourceFields(
            List<ResourceKey<StructureProcessorList>> processors, ResourceKey<LootTable> lootTable) {}
}
