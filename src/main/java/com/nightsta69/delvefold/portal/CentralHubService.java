package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

/** Plans and idempotently constructs the protected, player-facing central hub. */
final class CentralHubService {
    static final int PLATFORM_RADIUS = 5;
    static final int CLEAR_HEIGHT = PortalFrameShape.MIN_HEIGHT + 3;
    static final int DEFAULT_CAVERN_FLOOR_Y = 64;
    private static final int BORDER_MARGIN = PLATFORM_RADIUS + 1;
    private static final int BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState PLATFORM = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState MARKER = Blocks.LODESTONE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

    private CentralHubService() {}

    /**
     * Resolves one stable hub plan. Marker blocks make the chosen surface height persistent even after the hub itself
     * changes the heightmap, without retaining chunks or adding saved data.
     */
    static Optional<HubPlan> plan(ServerLevel level, PortalHubSettings settings) {
        if (!hasHubMargin(level.getWorldBorder(), settings.x(), settings.z())) {
            return Optional.empty();
        }

        Optional<HubPlan> existing = findMarkedPlan(level, settings);
        if (existing.isPresent()) {
            return existing;
        }

        int floorY;
        if (DelvefoldWorldgen.isCavernLevel(level.dimension())) {
            floorY = DEFAULT_CAVERN_FLOOR_Y;
        } else {
            floorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, settings.x(), settings.z()) - 1;
        }
        floorY = Mth.clamp(floorY, minimumFloorY(level), maximumFloorY(level));
        return Optional.of(planAtFloor(new BlockPos(settings.x(), floorY, settings.z())));
    }

    static PortalFrameShape build(ServerLevel level, HubPlan plan) {
        BlockPos center = plan.floorCenter();
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos floor = center.offset(x, 0, z);
                BlockState floorState = isMarkerOffset(x, z) ? MARKER : isLightOffset(x, z) ? LIGHT : PLATFORM;
                level.setBlock(floor, floorState, BLOCK_UPDATE_FLAGS);
                for (int vertical = 1; vertical <= CLEAR_HEIGHT; vertical++) {
                    level.setBlock(floor.above(vertical), Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
                }
            }
        }
        plan.returnPortal().buildAndFill(level);
        return plan.returnPortal();
    }

    static Optional<PortalFrameShape> ensureHub(ServerLevel level, PortalHubSettings settings) {
        return plan(level, settings).map(plan -> isIntact(level, plan) ? plan.returnPortal() : build(level, plan));
    }

    static HubPlan planAtFloor(BlockPos floorCenter) {
        BlockPos immutable = floorCenter.immutable();
        PortalFrameShape returnPortal = PortalFrameShape.standardAt(immutable.above(), Direction.Axis.X);
        return new HubPlan(immutable, returnPortal);
    }

    static boolean isIntact(ServerLevel level, HubPlan plan) {
        PortalFrameShape portal = plan.returnPortal();
        if (!portal.isFilled(level)) {
            return false;
        }
        Set<BlockPos> frame = new HashSet<>(portal.framePositions());
        Set<BlockPos> interior = new HashSet<>(portal.interiorPositions());
        BlockPos center = plan.floorCenter();
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos floor = center.offset(x, 0, z);
                if (frame.contains(floor)) {
                    if (!level.getBlockState(floor).is(PortalRegistries.PORTAL_FRAME.get())) {
                        return false;
                    }
                } else {
                    BlockState expected = isMarkerOffset(x, z) ? MARKER : isLightOffset(x, z) ? LIGHT : PLATFORM;
                    if (!level.getBlockState(floor).equals(expected)) {
                        return false;
                    }
                }
                for (int vertical = 1; vertical <= CLEAR_HEIGHT; vertical++) {
                    BlockPos position = floor.above(vertical);
                    BlockState state = level.getBlockState(position);
                    if (frame.contains(position)) {
                        if (!state.is(PortalRegistries.PORTAL_FRAME.get())) {
                            return false;
                        }
                    } else if (interior.contains(position)) {
                        if (!state.is(PortalRegistries.PORTAL.get())) {
                            return false;
                        }
                    } else if (!state.isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Optional<HubPlan> findMarkedPlan(ServerLevel level, PortalHubSettings settings) {
        int x = settings.x();
        int z = settings.z();
        for (int y = minimumFloorY(level); y <= maximumFloorY(level); y++) {
            BlockPos first = new BlockPos(x - PLATFORM_RADIUS, y, z - PLATFORM_RADIUS);
            BlockPos second = new BlockPos(x + PLATFORM_RADIUS, y, z + PLATFORM_RADIUS);
            if (level.getBlockState(first).is(Blocks.LODESTONE)
                    && level.getBlockState(second).is(Blocks.LODESTONE)) {
                return Optional.of(planAtFloor(new BlockPos(x, y, z)));
            }
        }
        return Optional.empty();
    }

    private static boolean isMarkerOffset(int x, int z) {
        return (x == -PLATFORM_RADIUS && z == -PLATFORM_RADIUS) || (x == PLATFORM_RADIUS && z == PLATFORM_RADIUS);
    }

    private static boolean isLightOffset(int x, int z) {
        return (x == -PLATFORM_RADIUS && z == PLATFORM_RADIUS)
                || (x == PLATFORM_RADIUS && z == -PLATFORM_RADIUS)
                || (x == 0 && Math.abs(z) == PLATFORM_RADIUS)
                || (z == 0 && Math.abs(x) == PLATFORM_RADIUS);
    }

    private static int minimumFloorY(ServerLevel level) {
        return level.getMinBuildHeight() + 1;
    }

    private static int maximumFloorY(ServerLevel level) {
        return level.getMaxBuildHeight() - CLEAR_HEIGHT - 1;
    }

    private static boolean hasHubMargin(WorldBorder border, int x, int z) {
        return x >= Math.ceil(border.getMinX() + BORDER_MARGIN)
                && x <= Math.floor(border.getMaxX() - BORDER_MARGIN)
                && z >= Math.ceil(border.getMinZ() + BORDER_MARGIN)
                && z <= Math.floor(border.getMaxZ() - BORDER_MARGIN);
    }

    record HubPlan(BlockPos floorCenter, PortalFrameShape returnPortal) {
        HubPlan {
            floorCenter = floorCenter.immutable();
        }
    }
}
