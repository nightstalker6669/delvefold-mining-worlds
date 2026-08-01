package com.nightsta69.delvefold.content;

import com.nightsta69.delvefold.guide.DelvefoldGuideService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Opens the same server-authorized read-only guide as /delvefold guide. */
public final class SeamLedgerItem extends Item {
    public SeamLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        DelvefoldGuideService.OpenResult result = DelvefoldGuideService.openFor(serverPlayer);
        if (!result.opened()) {
            serverPlayer.sendSystemMessage(result.message());
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }
}
