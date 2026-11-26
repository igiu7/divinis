package com.divinis.forge;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The Smelting Forge's controller, its firebox (SmeltingForge): its front faces the player who placed it; FORMED while
 * the forge around it is built right (then hidden: the renderer draws the furnace); FIRE its fire (out, a smoulder,
 * a full fire).
 */
public class SmeltingForgeBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SmeltingForgeBlock> CODEC = simpleCodec(SmeltingForgeBlock::new);
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty FIRE = SmeltingForge.FIRE;
    public static final BooleanProperty FORMED = ForgeBricksBlock.FORMED;

    public SmeltingForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FIRE, 0).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FIRE, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? net.minecraft.world.level.block.RenderShape.INVISIBLE : net.minecraft.world.level.block.RenderShape.MODEL;
    }

    /** Used before the forge is built (a formed one opens its station): how to build it. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!state.getValue(FORMED)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Build the forge: a 3x3x3 of Forge Bricks with this block at the front of the bottom layer; leave the "
                                + "block above it and the middle of the cube empty.").withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmeltingForgeBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SmeltingForge.BLOCK_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SmeltingForgeBlockEntity>) SmeltingForgeBlockEntity::serverTick;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SmeltingForgeBlockEntity forge) {
            for (var stack : forge.contents()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** While it burns: flames (a full fire) or a few sparks (a smoulder) in the firebox, smoke out of the chimney. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int fire = state.getValue(FIRE);
        if (fire == SmeltingForge.OUT || !state.getValue(FORMED)) return;
        Direction front = state.getValue(FACING);
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        double fx = front.getStepX(), fz = front.getStepZ();
        if (fire == SmeltingForge.SMOULDER) {
            if (random.nextDouble() < 0.3) {
                level.addParticle(ParticleTypes.SMOKE, x + fx * 0.3, y + 0.35, z + fz * 0.3, 0, 0.02, 0);
            }
            if (random.nextDouble() < 0.3) level.addParticle(ParticleTypes.SMOKE, x - fx, y + 5.2, z - fz, 0, 0.03, 0);
            return;
        }
        if (random.nextDouble() < 0.15) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        double side = (random.nextDouble() - 0.5) * 0.6;
        double ox = x + fx * 0.3 + (fz != 0 ? side : 0), oz = z + fz * 0.3 + (fx != 0 ? side : 0);
        level.addParticle(ParticleTypes.FLAME, ox, y + 0.28, oz, 0, 0.01, 0);
        if (random.nextDouble() < 0.3) level.addParticle(ParticleTypes.SMALL_FLAME, ox, y + 0.3, oz, 0, 0.02, 0);
        // the chimney stands a block behind the firebox, five blocks up
        double cx = x - fx, cz = z - fz;
        if (random.nextDouble() < 0.7) level.addParticle(ParticleTypes.LARGE_SMOKE, cx, y + 5.2, cz, 0, 0.05, 0);
        if (random.nextDouble() < 0.4) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, y + 5.2, cz, 0, 0.06, 0);
    }
}
