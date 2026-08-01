package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.world.landmark.catalog.LandmarkDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Serialized template piece that does not consult the live catalog after its structure start is created.
 *
 * <p>The definition ID, rotation, processor keys, loot table, and content seed are captured from one immutable catalog
 * snapshot and persisted with the piece. A later datapack reload therefore cannot change an existing start. Template
 * placement remains owned by Minecraft's structure pipeline, which clips writes to the currently processed chunk.
 */
public final class LandmarkTemplatePiece extends TemplateStructurePiece {
    private static final String TAG_LANDMARK = "Landmark";
    private static final String TAG_ROTATION = "Rotation";
    private static final String TAG_PROCESSORS = "Processors";
    private static final String TAG_LOOT_TABLE = "LootTable";
    private static final String TAG_CONTENT_SEED = "ContentSeed";
    private static final String PERSISTENT_LOOT_INITIALIZED = "DelvefoldLandmarkLootInitialized";

    private final ResourceLocation landmarkId;
    private final List<ResourceKey<StructureProcessorList>> processorKeys;
    private final ResourceKey<LootTable> lootTable;
    private final long contentSeed;

    /**
     * Captures a validated landmark definition into a new structure piece.
     *
     * @param templates server structure-template manager used by the platform structure pipeline
     * @param registryAccess active server registry view used to resolve processor lists
     * @param definition definition selected from one immutable catalog snapshot
     * @param position minimum template origin in block coordinates
     * @param rotation deterministic template rotation
     * @param contentSeed deterministic seed derived from world seed, chunk, generation salt, and definition ID
     */
    public LandmarkTemplatePiece(
            StructureTemplateManager templates,
            RegistryAccess registryAccess,
            LandmarkDefinition definition,
            BlockPos position,
            Rotation rotation,
            long contentSeed) {
        super(
                LandmarkRegistries.LANDMARK_PIECE.get(),
                0,
                templates,
                definition.template(),
                definition.template().toString(),
                settings(rotation, definition.processors(), registryAccess),
                position);
        this.landmarkId = definition.id();
        this.processorKeys = definition.processors();
        this.lootTable = definition.lootTable();
        this.contentSeed = contentSeed;
    }

    /**
     * Rehydrates a structure piece from chunk NBT without consulting the reloadable catalog.
     *
     * @param context platform serialization context providing templates and registries
     * @param tag persisted structure-piece data
     */
    public LandmarkTemplatePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(
                LandmarkRegistries.LANDMARK_PIECE.get(),
                tag,
                context.structureTemplateManager(),
                ignored -> settings(readRotation(tag), readProcessors(tag), context.registryAccess()));
        this.landmarkId = ResourceLocation.parse(tag.getString(TAG_LANDMARK));
        this.processorKeys = readProcessors(tag);
        this.lootTable =
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(tag.getString(TAG_LOOT_TABLE)));
        this.contentSeed = tag.getLong(TAG_CONTENT_SEED);
    }

    private static StructurePlaceSettings settings(
            Rotation rotation, List<ResourceKey<StructureProcessorList>> processors, RegistryAccess registryAccess) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        var registry = registryAccess.registryOrThrow(Registries.PROCESSOR_LIST);
        for (ResourceKey<StructureProcessorList> key : processors) {
            StructureProcessorList list = registry.get(key);
            if (list != null) {
                list.list().forEach(settings::addProcessor);
            }
        }
        return settings;
    }

    private static Rotation readRotation(CompoundTag tag) {
        try {
            return Rotation.valueOf(tag.getString(TAG_ROTATION));
        } catch (IllegalArgumentException exception) {
            return Rotation.NONE;
        }
    }

    private static List<ResourceKey<StructureProcessorList>> readProcessors(CompoundTag tag) {
        ListTag values = tag.getList(TAG_PROCESSORS, Tag.TAG_STRING);
        List<ResourceKey<StructureProcessorList>> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(values.getString(index));
            if (id != null) {
                result.add(ResourceKey.create(Registries.PROCESSOR_LIST, id));
            }
        }
        return List.copyOf(result);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(TAG_LANDMARK, landmarkId.toString());
        tag.putString(TAG_ROTATION, placeSettings.getRotation().name());
        ListTag processors = new ListTag();
        processorKeys.forEach(
                key -> processors.add(StringTag.valueOf(key.location().toString())));
        tag.put(TAG_PROCESSORS, processors);
        tag.putString(TAG_LOOT_TABLE, lootTable.location().toString());
        tag.putLong(TAG_CONTENT_SEED, contentSeed);
    }

    @Override
    protected void handleDataMarker(
            String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        if ("loot".equals(name)) {
            installLootOnce(level, pos.below(), lootTable, LandmarkSeeds.lootSeed(contentSeed, pos.asLong()));
        }
    }

    static boolean installLootOnce(
            ServerLevelAccessor level, BlockPos position, ResourceKey<LootTable> lootTable, long seed) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof RandomizableContainer container)) {
            return false;
        }
        if (blockEntity.getPersistentData().getBoolean(PERSISTENT_LOOT_INITIALIZED)
                || container.getLootTable() != null
                || !container.isEmpty()) {
            return false;
        }
        container.setLootTable(lootTable, seed);
        blockEntity.getPersistentData().putBoolean(PERSISTENT_LOOT_INITIALIZED, true);
        blockEntity.setChanged();
        return true;
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
    }

    /**
     * Returns the catalog ID captured when this structure start was created.
     *
     * @return immutable landmark definition ID
     */
    public ResourceLocation landmarkId() {
        return landmarkId;
    }
}
