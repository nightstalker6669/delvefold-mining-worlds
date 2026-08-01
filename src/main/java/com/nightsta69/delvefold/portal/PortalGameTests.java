package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortalGameTests {
    private static final String HUB_RESERVATION = "gametest/living_geology_reservation";

    private PortalGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void completeFramesFillAndBrokenFramesInvalidate(GameTestHelper helper) {
        PortalFrameShape shape =
                PortalFrameShape.standardAt(helper.absolutePos(new BlockPos(3, 2, 3)), Direction.Axis.X);
        shape.buildAndFill(helper.getLevel());

        helper.assertTrue(shape.isFilled(helper.getLevel()), "Complete frame did not fill with portal blocks");
        helper.assertTrue(
                PortalFrameShape.findFromInterior(helper.getLevel(), shape.bottomLeft(), Direction.Axis.X)
                        .isPresent(),
                "Filled frame was not discoverable");

        BlockPos brokenFrame = shape.framePositions().iterator().next();
        helper.getLevel().setBlockAndUpdate(brokenFrame, Blocks.AIR.defaultBlockState());
        helper.assertTrue(
                PortalFrameShape.findForIgnition(helper.getLevel(), brokenFrame).isEmpty(),
                "Broken frame remained ignitable");
        helper.succeed();
    }

    @GameTest(templateNamespace = Delvefold.MOD_ID, template = HUB_RESERVATION)
    public static void centralHubBuildsAStablePlatformAndReturnPortal(GameTestHelper helper) {
        BlockPos floorCenter = helper.absolutePos(new BlockPos(17, 1, 17));
        CentralHubService.HubPlan initial = CentralHubService.planAtFloor(floorCenter);
        PortalFrameShape returnPortal = CentralHubService.build(helper.getLevel(), initial);

        helper.assertTrue(
                returnPortal.isFilled(helper.getLevel()), "Central hub did not create its guaranteed return portal");
        helper.assertTrue(
                helper.getLevel().getBlockState(floorCenter.offset(2, 0, 2)).is(Blocks.POLISHED_DEEPSLATE),
                "Central hub platform did not use the expected safe vanilla block");
        helper.assertTrue(
                helper.getLevel()
                        .getBlockState(floorCenter.offset(
                                -CentralHubService.PLATFORM_RADIUS, 0, -CentralHubService.PLATFORM_RADIUS))
                        .is(Blocks.LODESTONE),
                "Central hub did not persist its deterministic floor marker");
        helper.assertTrue(
                helper.getLevel().getBlockState(floorCenter.offset(3, 3, 3)).isAir(),
                "Central hub did not clear safe headroom around the portal");
        helper.assertTrue(
                CentralHubService.isIntact(helper.getLevel(), initial),
                "A newly built central hub did not pass its safety verification");

        helper.getLevel().setBlockAndUpdate(returnPortal.bottomLeft(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                !CentralHubService.isIntact(helper.getLevel(), initial),
                "Central hub verification ignored a damaged portal");
        CentralHubService.HubPlan recovered = CentralHubService.plan(
                        helper.getLevel(),
                        new com.nightsta69.delvefold.config.model.PortalHubSettings(
                                floorCenter.getX(), floorCenter.getZ(), 16))
                .orElseThrow();
        PortalFrameShape rebuilt = CentralHubService.build(helper.getLevel(), recovered);
        helper.assertTrue(
                recovered.floorCenter().equals(floorCenter),
                "Hub markers did not retain the selected vertical position");
        helper.assertTrue(rebuilt.isFilled(helper.getLevel()), "Central hub did not repair its return portal");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void portalRoutingRemainsPlayerOnly(GameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        var transition = PortalRegistries.PORTAL
                .get()
                .getPortalDestination(helper.getLevel(), pig, helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(transition == null, "A non-player entity received a Delvefold portal route");
        pig.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void centralHubEventsDenyOrdinaryPlayersAndAllowWorldManagers(GameTestHelper helper) {
        BlockPos protectedPosition = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(protectedPosition, Blocks.STONE.defaultBlockState());
        PortalSettings settings = new PortalSettings(
                true,
                true,
                5,
                1.0D,
                PortalRoutingMode.CENTRAL_HUB,
                new PortalHubSettings(protectedPosition.getX(), protectedPosition.getZ(), 16));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var playerList = helper.getLevel().getServer().getPlayerList();
        playerList.deop(player.getGameProfile());
        try {
            helper.assertTrue(
                    !AdminAccess.canManageWorld(player),
                    "An ordinary player unexpectedly had world-management permission");

            BlockEvent.BreakEvent deniedBreak = new BlockEvent.BreakEvent(
                    helper.getLevel(), protectedPosition,
                    helper.getLevel().getBlockState(protectedPosition), player);
            CentralHubProtectionEvents.applyBreak(deniedBreak, settings, true);
            helper.assertTrue(
                    deniedBreak.isCanceled(), "Central-hub protection did not cancel an ordinary player's block break");

            BlockEvent.EntityPlaceEvent deniedPlace = new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), protectedPosition),
                    Blocks.STONE.defaultBlockState(),
                    player);
            CentralHubProtectionEvents.applyPlace(deniedPlace, settings, true);
            helper.assertTrue(
                    deniedPlace.isCanceled(),
                    "Central-hub protection did not cancel an ordinary player's block placement");

            BlockHitResult hit =
                    new BlockHitResult(Vec3.atCenterOf(protectedPosition), Direction.UP, protectedPosition, false);
            PlayerInteractEvent.RightClickBlock deniedInteraction =
                    new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, protectedPosition, hit);
            CentralHubProtectionEvents.applyRightClick(deniedInteraction, settings, true);
            helper.assertTrue(
                    deniedInteraction.isCanceled()
                            && deniedInteraction.getCancellationResult() == InteractionResult.FAIL,
                    "Central-hub protection did not deny an ordinary player's block interaction");

            // GameTestServer reports an operator permission level of zero, so PlayerList#op
            // would create a level-zero entry. Install the same explicit level-four entry a
            // dedicated server uses for Delvefold world managers.
            playerList
                    .getOps()
                    .add(new ServerOpListEntry(
                            player.getGameProfile(), AdminAccess.WORLD_MANAGEMENT_PERMISSION, false));
            helper.assertTrue(
                    AdminAccess.canManageWorld(player), "An operator did not receive world-management permission");

            BlockEvent.BreakEvent allowedBreak = new BlockEvent.BreakEvent(
                    helper.getLevel(), protectedPosition,
                    helper.getLevel().getBlockState(protectedPosition), player);
            CentralHubProtectionEvents.applyBreak(allowedBreak, settings, true);
            helper.assertTrue(
                    !allowedBreak.isCanceled(), "Central-hub protection canceled a world manager's block break");

            BlockEvent.EntityPlaceEvent allowedPlace = new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), protectedPosition),
                    Blocks.STONE.defaultBlockState(),
                    player);
            CentralHubProtectionEvents.applyPlace(allowedPlace, settings, true);
            helper.assertTrue(
                    !allowedPlace.isCanceled(), "Central-hub protection canceled a world manager's block placement");

            PlayerInteractEvent.RightClickBlock allowedInteraction =
                    new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, protectedPosition, hit);
            CentralHubProtectionEvents.applyRightClick(allowedInteraction, settings, true);
            helper.assertTrue(
                    !allowedInteraction.isCanceled(), "Central-hub protection canceled a world manager's interaction");
        } finally {
            playerList.deop(player.getGameProfile());
            player.discard();
        }
        helper.succeed();
    }
}
