package com.nightsta69.delvefold.portal;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/** A Delvefold frame block ignited with vanilla Flint and Steel. */
public final class PortalFrameBlock extends Block {
    public static final MapCodec<PortalFrameBlock> CODEC = simpleCodec(PortalFrameBlock::new);

    public PortalFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PortalFrameBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(Items.FLINT_AND_STEEL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.FAIL;
        }

        PortalAccess.Result access = PortalAccess.forIgnition(serverLevel, serverPlayer);
        if (!access.allowed()) {
            PortalAccess.notifyDenied(serverPlayer, access);
            return ItemInteractionResult.FAIL;
        }

        Optional<PortalFrameShape> found = PortalFrameShape.findForIgnition(serverLevel, position);
        if (found.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.delvefold.portal.invalid_frame"), true);
            return ItemInteractionResult.FAIL;
        }

        PortalFrameShape shape = found.get();
        if (shape.isFilled(serverLevel)) {
            serverPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.delvefold.portal.already_active"), true);
            return ItemInteractionResult.FAIL;
        }

        shape.fill(serverLevel);
        serverLevel.playSound(
                null,
                position,
                SoundEvents.FLINTANDSTEEL_USE,
                SoundSource.BLOCKS,
                1.0F,
                0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
        serverLevel.gameEvent(serverPlayer, GameEvent.BLOCK_CHANGE, position);
        CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, position, stack);
        stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(hand));
        serverPlayer.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("message.delvefold.portal.activated"), true);
        return ItemInteractionResult.CONSUME;
    }
}
