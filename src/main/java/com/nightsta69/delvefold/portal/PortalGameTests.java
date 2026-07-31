package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.Delvefold;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortalGameTests {
    private PortalGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void completeFramesFillAndBrokenFramesInvalidate(GameTestHelper helper) {
        PortalFrameShape shape = PortalFrameShape.standardAt(
                helper.absolutePos(new BlockPos(3, 2, 3)), Direction.Axis.X);
        shape.buildAndFill(helper.getLevel());

        helper.assertTrue(shape.isFilled(helper.getLevel()), "Complete frame did not fill with portal blocks");
        helper.assertTrue(PortalFrameShape.findFromInterior(
                helper.getLevel(), shape.bottomLeft(), Direction.Axis.X).isPresent(),
                "Filled frame was not discoverable");

        BlockPos brokenFrame = shape.framePositions().iterator().next();
        helper.getLevel().setBlockAndUpdate(brokenFrame, Blocks.AIR.defaultBlockState());
        helper.assertTrue(PortalFrameShape.findForIgnition(helper.getLevel(), brokenFrame).isEmpty(),
                "Broken frame remained ignitable");
        helper.succeed();
    }
}
