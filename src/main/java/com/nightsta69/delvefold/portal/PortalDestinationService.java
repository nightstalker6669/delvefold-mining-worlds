package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.reset.MiningPlayerSafety;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

/**
 * Finds or builds a bounded, safe partner portal and creates the 1.21.1
 * {@link DimensionTransition}. No region ticket or forced chunk is retained.
 */
final class PortalDestinationService {
    private static final int PORTAL_SEARCH_RADIUS = 32;
    private static final int NATURAL_SITE_RADIUS = 16;
    private static final int CAVERN_VERTICAL_SEARCH = 32;
    private static final int BORDER_MARGIN = 4;
    private static final int FALLBACK_CLEAR_RADIUS = 2;
    private static final int BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private PortalDestinationService() {
    }

    static DimensionTransition createTransition(
            ServerLevel source,
            ServerPlayer player,
            BlockPos entryPosition,
            PortalAccess.Result access) {
        ServerLevel target = access.destination();
        if (target == null) {
            fail(player, access);
            return null;
        }

        BlockState entryState = source.getBlockState(entryPosition);
        Optional<Direction.Axis> entryAxis = entryState.getOptionalValue(MiningPortalBlock.AXIS);
        if (entryAxis.isEmpty()) {
            fail(player, access);
            return null;
        }

        Optional<PortalFrameShape> sourceShape = PortalFrameShape.findFromInterior(
                        source, entryPosition, entryAxis.get())
                .filter(shape -> shape.isFilled(source));
        if (sourceShape.isEmpty()) {
            fail(player, access);
            return null;
        }

        Optional<BlockPos> requested = requestedPosition(target, player, access);
        if (requested.isEmpty()) {
            fail(player, access);
            return null;
        }

        Direction.Axis sourcePortalAxis = sourceShape.get().axis();
        Optional<PortalFrameShape> targetShape = findClosestPortal(target, requested.get())
                .or(() -> createPortal(target, requested.get(), sourcePortalAxis));
        if (targetShape.isEmpty()) {
            fail(player, access);
            return null;
        }

        BlockUtil.FoundRectangle sourceRectangle = rectangle(sourceShape.get());
        Vec3 relativePosition = player.getRelativePortalPosition(sourcePortalAxis, sourceRectangle);
        return transitionTo(
                target,
                player,
                targetShape.get(),
                sourcePortalAxis,
                relativePosition,
                PortalAccess.cooldownTicks(access.settings()));
    }

    private static Optional<BlockPos> requestedPosition(
            ServerLevel target, ServerPlayer player, PortalAccess.Result access) {
        double configuredScale = PortalAccess.coordinateScale(access.settings());
        double factor = access.returningToOverworld() ? 1.0D / configuredScale : configuredScale;
        double x = player.getX() * factor;
        double z = player.getZ() * factor;
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            x = 0.0D;
            z = 0.0D;
        }

        WorldBorder border = target.getWorldBorder();
        Optional<HorizontalPosition> clamped = clampInsideBorder(border, x, z);
        if (clamped.isEmpty()) {
            return Optional.empty();
        }

        int minimumY = target.getMinBuildHeight() + 2;
        int maximumY = target.getMaxBuildHeight() - 5;
        if (minimumY > maximumY) {
            return Optional.empty();
        }
        int y = Mth.clamp(Mth.floor(player.getY()), minimumY, maximumY);
        return Optional.of(new BlockPos(clamped.get().x(), y, clamped.get().z()));
    }

    private static Optional<PortalFrameShape> findClosestPortal(ServerLevel level, BlockPos requested) {
        PoiManager poiManager = level.getPoiManager();
        poiManager.ensureLoadedAndValid(level, requested, PORTAL_SEARCH_RADIUS);
        WorldBorder border = level.getWorldBorder();

        return poiManager
                .getInSquare(
                        holder -> holder.is(PortalRegistries.PORTAL_POI_KEY),
                        requested,
                        PORTAL_SEARCH_RADIUS,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(border::isWithinBounds)
                .sorted(Comparator.<BlockPos>comparingDouble(position -> position.distSqr(requested))
                        .thenComparingInt(Vec3i::getY))
                .map(position -> shapeAtPortal(level, position))
                .flatMap(Optional::stream)
                .filter(shape -> shape.isFilled(level))
                .findFirst();
    }

    private static Optional<PortalFrameShape> shapeAtPortal(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (!state.is(PortalRegistries.PORTAL.get())) {
            return Optional.empty();
        }
        return PortalFrameShape.findFromInterior(level, position, state.getValue(MiningPortalBlock.AXIS));
    }

    private static Optional<PortalFrameShape> createPortal(
            ServerLevel level, BlockPos requested, Direction.Axis axis) {
        Optional<PortalFrameShape> natural = findNaturalSite(level, requested, axis);
        if (natural.isPresent()) {
            natural.get().buildAndFill(level);
            return natural;
        }
        return createFallbackPortal(level, requested, axis);
    }

    private static Optional<PortalFrameShape> findNaturalSite(
            ServerLevel level, BlockPos requested, Direction.Axis axis) {
        boolean cavern = com.nightsta69.delvefold.world.DelvefoldWorldgen.isCavernLevel(level.dimension());
        WorldBorder border = level.getWorldBorder();

        for (BlockPos.MutableBlockPos cursor : BlockPos.spiralAround(
                requested, NATURAL_SITE_RADIUS, Direction.EAST, Direction.SOUTH)) {
            if (!hasPortalMargin(border, cursor.getX(), cursor.getZ())) {
                continue;
            }

            int preferredY = cavern
                    ? requested.getY()
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cursor.getX(), cursor.getZ());
            preferredY = clampPortalY(level, preferredY);

            if (!cavern) {
                PortalFrameShape shape = PortalFrameShape.standardAt(
                        new BlockPos(cursor.getX(), preferredY, cursor.getZ()), axis);
                if (canHostNaturalPortal(level, shape)) {
                    return Optional.of(shape);
                }
                continue;
            }

            for (int distance = 0; distance <= CAVERN_VERTICAL_SEARCH; distance++) {
                int above = clampPortalY(level, preferredY + distance);
                PortalFrameShape aboveShape = PortalFrameShape.standardAt(
                        new BlockPos(cursor.getX(), above, cursor.getZ()), axis);
                if (canHostNaturalPortal(level, aboveShape)) {
                    return Optional.of(aboveShape);
                }

                if (distance > 0) {
                    int below = clampPortalY(level, preferredY - distance);
                    if (below != above) {
                        PortalFrameShape belowShape = PortalFrameShape.standardAt(
                                new BlockPos(cursor.getX(), below, cursor.getZ()), axis);
                        if (canHostNaturalPortal(level, belowShape)) {
                            return Optional.of(belowShape);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canHostNaturalPortal(ServerLevel level, PortalFrameShape shape) {
        int bottomFrameY = shape.bottomLeft().getY() - 1;
        for (BlockPos framePosition : shape.framePositions()) {
            BlockState state = level.getBlockState(framePosition);
            if (framePosition.getY() == bottomFrameY) {
                if (!state.isSolid()) {
                    return false;
                }
            } else if (!isReplaceableAndDry(state)) {
                return false;
            }
        }

        Direction width = PortalFrameShape.positiveDirection(shape.axis());
        Direction normal = width.getClockWise();
        for (int across = 0; across < shape.width(); across++) {
            for (int y = 0; y < shape.height(); y++) {
                BlockPos interior = shape.bottomLeft().relative(width, across).above(y);
                for (int depth = -1; depth <= 1; depth++) {
                    if (!isReplaceableAndDry(level.getBlockState(interior.relative(normal, depth)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Optional<PortalFrameShape> createFallbackPortal(
            ServerLevel level, BlockPos requested, Direction.Axis axis) {
        Optional<HorizontalPosition> clamped = clampInsideBorder(
                level.getWorldBorder(), requested.getX(), requested.getZ());
        if (clamped.isEmpty()) {
            return Optional.empty();
        }

        int y;
        if (com.nightsta69.delvefold.world.DelvefoldWorldgen.isCavernLevel(level.dimension())) {
            y = clampPortalY(level, requested.getY());
        } else {
            y = clampPortalY(level, level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    clamped.get().x(),
                    clamped.get().z()));
        }

        BlockPos center = new BlockPos(clamped.get().x(), y, clamped.get().z());
        PortalFrameShape shape = PortalFrameShape.standardAt(center, axis);
        Direction width = PortalFrameShape.positiveDirection(axis);
        Direction normal = width.getClockWise();
        BlockState platform = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

        for (int across = -FALLBACK_CLEAR_RADIUS; across <= FALLBACK_CLEAR_RADIUS; across++) {
            for (int depth = -FALLBACK_CLEAR_RADIUS; depth <= FALLBACK_CLEAR_RADIUS; depth++) {
                BlockPos column = center.relative(width, across).relative(normal, depth);
                level.setBlock(column.below(), platform, BLOCK_UPDATE_FLAGS);
                for (int vertical = 0; vertical <= PortalFrameShape.MIN_HEIGHT + 1; vertical++) {
                    level.setBlock(column.above(vertical), Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
                }
            }
        }

        shape.buildAndFill(level);
        return Optional.of(shape);
    }

    private static DimensionTransition transitionTo(
            ServerLevel target,
            ServerPlayer player,
            PortalFrameShape targetShape,
            Direction.Axis sourceAxis,
            Vec3 relativePosition,
            int cooldownTicks) {
        Direction.Axis targetAxis = targetShape.axis();
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        BlockPos corner = targetShape.bottomLeft();
        double usableWidth = targetShape.width();
        double usableHeight = targetShape.height();
        int quarterTurn = sourceAxis == targetAxis ? 0 : 90;
        Vec3 velocity = sourceAxis == targetAxis
                ? player.getDeltaMovement()
                : new Vec3(player.getDeltaMovement().z, player.getDeltaMovement().y, -player.getDeltaMovement().x);

        double along = dimensions.width() / 2.0D
                + (usableWidth - dimensions.width()) * relativePosition.x();
        double vertical = (usableHeight - dimensions.height()) * relativePosition.y();
        double normal = 0.5D + relativePosition.z();
        boolean xAxis = targetAxis == Direction.Axis.X;
        Vec3 proposed = new Vec3(
                corner.getX() + (xAxis ? along : normal),
                corner.getY() + vertical,
                corner.getZ() + (xAxis ? normal : along));
        Vec3 safe = PortalShape.findCollisionFreePosition(proposed, target, player, dimensions);

        return new DimensionTransition(
                target,
                safe,
                velocity,
                player.getYRot() + quarterTurn,
                player.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(entity -> {
                    entity.setPortalCooldown(cooldownTicks);
                    if (entity instanceof ServerPlayer movedPlayer && PortalAccess.isMiningLevel(target.dimension())) {
                        MiningPlayerSafety.markCurrentEpoch(movedPlayer);
                    }
                }));
    }

    private static BlockUtil.FoundRectangle rectangle(PortalFrameShape shape) {
        return new BlockUtil.FoundRectangle(shape.bottomLeft(), shape.width(), shape.height());
    }

    private static boolean isReplaceableAndDry(BlockState state) {
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static int clampPortalY(ServerLevel level, int y) {
        return Mth.clamp(y, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 5);
    }

    private static boolean hasPortalMargin(WorldBorder border, int x, int z) {
        return x >= Math.ceil(border.getMinX() + BORDER_MARGIN)
                && x <= Math.floor(border.getMaxX() - BORDER_MARGIN)
                && z >= Math.ceil(border.getMinZ() + BORDER_MARGIN)
                && z <= Math.floor(border.getMaxZ() - BORDER_MARGIN);
    }

    private static Optional<HorizontalPosition> clampInsideBorder(WorldBorder border, double x, double z) {
        int minimumX = Mth.ceil(border.getMinX() + BORDER_MARGIN);
        int maximumX = Mth.floor(border.getMaxX() - BORDER_MARGIN);
        int minimumZ = Mth.ceil(border.getMinZ() + BORDER_MARGIN);
        int maximumZ = Mth.floor(border.getMaxZ() - BORDER_MARGIN);
        if (minimumX > maximumX || minimumZ > maximumZ) {
            return Optional.empty();
        }
        int clampedX = Mth.clamp(Mth.floor(x), minimumX, maximumX);
        int clampedZ = Mth.clamp(Mth.floor(z), minimumZ, maximumZ);
        return Optional.of(new HorizontalPosition(clampedX, clampedZ));
    }

    private static void fail(ServerPlayer player, PortalAccess.Result access) {
        player.sendSystemMessage(Component.translatable("message.delvefold.portal.destination_failed"), true);
        player.setPortalCooldown(PortalAccess.cooldownTicks(access.settings()));
    }

    private record HorizontalPosition(int x, int z) {
    }
}
