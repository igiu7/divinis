package com.divinis.dungeon;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deepslate Crystal: dark blue crystals (the Golem's) growing out of a low bed of deepslate. The model comes from
 * Blender (tools/models/build_crystal_block.py); the crystals glow faintly. Its shape: the bed and the tall crystal.
 */
public class DeepslateCrystalBlock extends Block {
    public static final MapCodec<DeepslateCrystalBlock> CODEC = simpleCodec(DeepslateCrystalBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(5, 6, 5, 11, 15, 11));

    public DeepslateCrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
