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
    public static final TagKey<Structure> LANDMARKS =
            TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "landmarks"));

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Delvefold.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Delvefold.MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Delvefold.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<DelvefoldLandmarkStructure>> LANDMARK_STRUCTURE =
            STRUCTURE_TYPES.register("landmark", () -> () -> DelvefoldLandmarkStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LANDMARK_PIECE =
            PIECE_TYPES.register("landmark_piece", () -> LandmarkTemplatePiece::new);
    public static final DeferredHolder<
                    StructurePlacementType<?>, StructurePlacementType<GenerationSaltedRandomSpreadPlacement>>
            GENERATION_SALTED_RANDOM_SPREAD = PLACEMENT_TYPES.register(
                    "generation_salted_random_spread", () -> () -> GenerationSaltedRandomSpreadPlacement.CODEC);

    private LandmarkRegistries() {}

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        PLACEMENT_TYPES.register(modBus);
    }
}
