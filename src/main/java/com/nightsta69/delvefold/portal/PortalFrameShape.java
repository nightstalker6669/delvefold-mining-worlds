package com.nightsta69.delvefold.portal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Validates and fills rectangular Delvefold frames with a 2x3 through 21x21 interior. */
record PortalFrameShape(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
    static final int MIN_WIDTH = 2;
    static final int MIN_HEIGHT = 3;
    static final int MAX_WIDTH = 21;
    static final int MAX_HEIGHT = 21;

    PortalFrameShape {
        bottomLeft = bottomLeft.immutable();
        if (!axis.isHorizontal()
                || width < MIN_WIDTH
                || width > MAX_WIDTH
                || height < MIN_HEIGHT
                || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Invalid Delvefold portal dimensions");
        }
    }

    static Optional<PortalFrameShape> findForIgnition(LevelAccessor level, BlockPos clickedFrame) {
        if (!isFrame(level.getBlockState(clickedFrame))) {
            return Optional.empty();
        }

        for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
            Direction widthDirection = positiveDirection(axis);
            for (int verticalOffset = -1; verticalOffset <= 1; verticalOffset++) {
                for (int widthOffset = -1; widthOffset <= 1; widthOffset++) {
                    BlockPos candidate =
                            clickedFrame.relative(widthDirection, widthOffset).above(verticalOffset);
                    Optional<PortalFrameShape> shape = findFromInterior(level, candidate, axis);
                    if (shape.isPresent() && shape.get().isFramePosition(clickedFrame)) {
                        return shape;
                    }
                }
            }
        }
        return Optional.empty();
    }

    static Optional<PortalFrameShape> findFromInterior(
            LevelAccessor level, BlockPos interiorPosition, Direction.Axis axis) {
        if (!axis.isHorizontal() || !isInterior(level.getBlockState(interiorPosition))) {
            return Optional.empty();
        }

        BlockPos bottom = interiorPosition;
        for (int moved = 0; moved < MAX_HEIGHT && isInterior(level.getBlockState(bottom.below())); moved++) {
            bottom = bottom.below();
        }
        if (isInterior(level.getBlockState(bottom.below()))) {
            return Optional.empty();
        }

        Direction positive = positiveDirection(axis);
        Direction negative = positive.getOpposite();
        BlockPos bottomLeft = bottom;
        for (int moved = 0;
                moved < MAX_WIDTH && isInterior(level.getBlockState(bottomLeft.relative(negative)));
                moved++) {
            bottomLeft = bottomLeft.relative(negative);
        }
        if (!isFrame(level.getBlockState(bottomLeft.relative(negative)))) {
            return Optional.empty();
        }

        int width = 0;
        while (width <= MAX_WIDTH && isInterior(level.getBlockState(bottomLeft.relative(positive, width)))) {
            width++;
        }
        if (width < MIN_WIDTH
                || width > MAX_WIDTH
                || !isFrame(level.getBlockState(bottomLeft.relative(positive, width)))) {
            return Optional.empty();
        }

        for (int offset = -1; offset <= width; offset++) {
            if (!isFrame(
                    level.getBlockState(bottomLeft.relative(positive, offset).below()))) {
                return Optional.empty();
            }
        }

        for (int y = 0; y <= MAX_HEIGHT; y++) {
            if (isFullFrameRow(level, bottomLeft, positive, width, y)) {
                return y >= MIN_HEIGHT
                        ? Optional.of(new PortalFrameShape(bottomLeft, axis, width, y))
                        : Optional.empty();
            }
            if (y == MAX_HEIGHT
                    || !isFrame(
                            level.getBlockState(bottomLeft.relative(negative).above(y)))
                    || !isFrame(level.getBlockState(
                            bottomLeft.relative(positive, width).above(y)))) {
                return Optional.empty();
            }
            for (int offset = 0; offset < width; offset++) {
                if (!isInterior(level.getBlockState(
                        bottomLeft.relative(positive, offset).above(y)))) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    static PortalFrameShape standardAt(BlockPos bottomCenter, Direction.Axis axis) {
        Direction negative = positiveDirection(axis).getOpposite();
        return new PortalFrameShape(bottomCenter.relative(negative), axis, MIN_WIDTH, MIN_HEIGHT);
    }

    boolean isFilled(LevelAccessor level) {
        for (BlockPos position : interiorPositions()) {
            BlockState state = level.getBlockState(position);
            if (!state.is(PortalRegistries.PORTAL.get()) || state.getValue(MiningPortalBlock.AXIS) != axis) {
                return false;
            }
        }
        return true;
    }

    void fill(ServerLevel level) {
        BlockState portal = PortalRegistries.PORTAL.get().defaultBlockState().setValue(MiningPortalBlock.AXIS, axis);
        for (BlockPos position : interiorPositions()) {
            // Suppress shape propagation until every interior cell exists; otherwise
            // the first cells would invalidate themselves while the rectangle is partial.
            level.setBlock(position, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        notifyNeighbors(level);
    }

    void buildAndFill(ServerLevel level) {
        BlockState frame = PortalRegistries.PORTAL_FRAME.get().defaultBlockState();
        for (BlockPos position : framePositions()) {
            level.setBlock(position, frame, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        fill(level);
    }

    List<BlockPos> interiorPositions() {
        List<BlockPos> positions = new ArrayList<>(width * height);
        Direction positive = positiveDirection(axis);
        for (int y = 0; y < height; y++) {
            for (int offset = 0; offset < width; offset++) {
                positions.add(bottomLeft.relative(positive, offset).above(y));
            }
        }
        return positions;
    }

    Set<BlockPos> framePositions() {
        Set<BlockPos> positions = new LinkedHashSet<>();
        Direction positive = positiveDirection(axis);
        Direction negative = positive.getOpposite();
        for (int offset = -1; offset <= width; offset++) {
            positions.add(bottomLeft.relative(positive, offset).below());
            positions.add(bottomLeft.relative(positive, offset).above(height));
        }
        for (int y = 0; y < height; y++) {
            positions.add(bottomLeft.relative(negative).above(y));
            positions.add(bottomLeft.relative(positive, width).above(y));
        }
        return positions;
    }

    boolean isFramePosition(BlockPos position) {
        return framePositions().contains(position);
    }

    private void notifyNeighbors(ServerLevel level) {
        for (BlockPos position : framePositions()) {
            level.updateNeighborsAt(position, PortalRegistries.PORTAL_FRAME.get());
        }
        for (BlockPos position : interiorPositions()) {
            level.updateNeighborsAt(position, PortalRegistries.PORTAL.get());
        }
    }

    private static boolean isFullFrameRow(
            LevelAccessor level, BlockPos bottomLeft, Direction positive, int width, int y) {
        for (int offset = -1; offset <= width; offset++) {
            if (!isFrame(
                    level.getBlockState(bottomLeft.relative(positive, offset).above(y)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFrame(BlockState state) {
        return state.is(PortalRegistries.PORTAL_FRAME.get());
    }

    private static boolean isInterior(BlockState state) {
        return state.isAir() || state.is(PortalRegistries.PORTAL.get()) || state.is(BlockTags.FIRE);
    }

    static Direction positiveDirection(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }
}
