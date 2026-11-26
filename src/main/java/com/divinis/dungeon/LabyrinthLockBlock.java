package com.divinis.dungeon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The keyhole plate in a locked Labyrinth door. Using the right key on it (DungeonService) opens the whole door.
 * Cannot be broken.
 */
public class LabyrinthLockBlock extends Block {
    public static final EnumProperty<KeyType> KEY = EnumProperty.create("key", KeyType.class);

    public LabyrinthLockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KEY, KeyType.BRONZE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEY);
    }
}
