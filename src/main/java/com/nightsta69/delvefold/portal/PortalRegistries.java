package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.content.SeamLedgerItem;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Static registrations for the Delvefold portal and creative tab. */
public final class PortalRegistries {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Delvefold.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Delvefold.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Delvefold.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Delvefold.MOD_ID);

    public static final ResourceKey<PoiType> PORTAL_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "portal"));

    public static final DeferredHolder<Block, PortalFrameBlock> PORTAL_FRAME = BLOCKS.register(
            "portal_frame",
            () -> new PortalFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS)));

    public static final DeferredHolder<Block, MiningPortalBlock> PORTAL = BLOCKS.register(
            "portal",
            () -> new MiningPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .strength(-1.0F)
                    .lightLevel(state -> 11)
                    .sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()));

    public static final DeferredHolder<Item, BlockItem> PORTAL_FRAME_ITEM = ITEMS.register(
            "portal_frame", () -> new BlockItem(PORTAL_FRAME.get(), new Item.Properties()));

    public static final DeferredHolder<Item, SeamLedgerItem> SEAM_LEDGER = ITEMS.register(
            "seam_ledger", () -> new SeamLedgerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DELVEFOLD_TAB = CREATIVE_TABS.register(
            "mining_worlds",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.delvefold.mining_worlds"))
                    .icon(() -> new ItemStack(PORTAL_FRAME_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PORTAL_FRAME_ITEM.get());
                        output.accept(SEAM_LEDGER.get());
                    })
                    .build());

    public static final DeferredHolder<PoiType, PoiType> PORTAL_POI = POI_TYPES.register(
            "portal",
            () -> new PoiType(
                    Set.copyOf(PORTAL.get().getStateDefinition().getPossibleStates()),
                    0,
                    1));

    private PortalRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
