package com.nightsta69.delvefold.portal;

import com.mojang.serialization.MapCodec;
import com.nightsta69.delvefold.api.event.DelvefoldPortalTravelEvent;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;

/** Player-only Delvefold portal using the 1.21.1 Portal/DimensionTransition pipeline. */
public final class MiningPortalBlock extends Block implements Portal {
    public static final MapCodec<MiningPortalBlock> CODEC = simpleCodec(MiningPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape Z_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public MiningPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends MiningPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos position,
            BlockPos neighborPosition) {
        boolean complete = PortalFrameShape.findFromInterior(level, position, state.getValue(AXIS))
                .filter(shape -> shape.isFilled(level))
                .isPresent();
        return complete ? super.updateShape(state, direction, neighborState, level, position, neighborPosition)
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos position, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)
                || player.isOnPortalCooldown()
                || !player.canUsePortal(false)) {
            return;
        }

        PortalAccess.Result access = PortalAccess.forTransition(serverLevel, player);
        if (access.allowed()) {
            player.setAsInsidePortal(this, position);
        } else {
            PortalAccess.notifyDenied(player, access);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel source, Entity entity, BlockPos entryPosition) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        PortalAccess.Result access = PortalAccess.forTransition(source, player);
        if (!access.allowed()) {
            PortalAccess.notifyDenied(player, access);
            player.setPortalCooldown(PortalAccess.cooldownTicks(access.settings()));
            return null;
        }
        DelvefoldPortalTravelEvent event = NeoForge.EVENT_BUS.post(new DelvefoldPortalTravelEvent(
                player, source.dimension(), access.destination().dimension()));
        if (event.isCanceled()) {
            player.setPortalCooldown(PortalAccess.cooldownTicks(access.settings()));
            return null;
        }
        return PortalDestinationService.createTransition(source, player, entryPosition, access);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D,
                    SoundEvents.PORTAL_AMBIENT,
                    SoundSource.BLOCKS,
                    0.45F,
                    0.9F + random.nextFloat() * 0.2F,
                    false);
        }
        if (random.nextInt(180) == 0) {
            level.playLocalSound(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS,
                    0.22F,
                    0.65F + random.nextFloat() * 0.2F,
                    false);
        }
        for (int count = 0; count < 3; count++) {
            double x = position.getX() + random.nextDouble();
            double y = position.getY() + random.nextDouble();
            double z = position.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5D) * 0.25D;
            double dy = (random.nextDouble() - 0.5D) * 0.25D;
            double dz = (random.nextDouble() - 0.5D) * 0.25D;
            level.addParticle(count == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                    x, y, z, dx, dy, dz);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(
                    AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
