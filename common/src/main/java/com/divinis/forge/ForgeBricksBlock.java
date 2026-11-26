package com.divinis.forge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Forge Bricks: what the Smelting Forge is built of (SmeltingForge.structure). While they are part of a formed forge
 * (FORMED) they are not drawn: the forge's renderer draws the whole furnace in their place.
 */
public class ForgeBricksBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ForgeBricksBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
