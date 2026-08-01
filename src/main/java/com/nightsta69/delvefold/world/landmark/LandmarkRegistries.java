package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.Delvefold;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrations for the reloadable landmark structure subsystem. */
public final class LandmarkRegistries {
    /** Structure tag used by discovery checks to identify every Delvefold landmark structure. */
    public static final TagKey<Structure> LANDMARKS =
            TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "landmarks"));

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Delvefold.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Delvefold.MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Delvefold.MOD_ID);

    /** Deferred structure type whose codec creates deterministic reloadable landmark starts. */
    public static final DeferredHolder<StructureType<?>, StructureType<DelvefoldLandmarkStructure>> LANDMARK_STRUCTURE =
            STRUCTURE_TYPES.register("landmark", () -> () -> DelvefoldLandmarkStructure.CODEC);
    /** Serialized structure-piece type for catalog-independent landmark templates. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LANDMARK_PIECE =
            PIECE_TYPES.register("landmark_piece", () -> LandmarkTemplatePiece::new);
    /** Random-spread placement type that incorporates the persisted generation salt. */
    public static final DeferredHolder<
                    StructurePlacementType<?>, StructurePlacementType<GenerationSaltedRandomSpreadPlacement>>
            GENERATION_SALTED_RANDOM_SPREAD = PLACEMENT_TYPES.register(
                    "generation_salted_random_spread", () -> () -> GenerationSaltedRandomSpreadPlacement.CODEC);

    private LandmarkRegistries() {}

    /**
     * Attaches landmark structure, piece, and placement registrations to the mod lifecycle bus.
     *
     * @param modBus mod-scoped NeoForge registration bus; call during mod construction
     */
    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        PLACEMENT_TYPES.register(modBus);
    }
}
