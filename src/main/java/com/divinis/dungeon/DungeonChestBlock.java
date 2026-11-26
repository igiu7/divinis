package com.divinis.dungeon;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Looks like a chest; opening it shows each player of the party his own loot (DungeonChestBlockEntity). */
public class DungeonChestBlock extends ChestBlock {
    public static final MapCodec<DungeonChestBlock> CODEC = simpleCodec(DungeonChestBlock::new);

    public DungeonChestBlock(Properties properties) {
        super(properties, DungeonRegistry.DUNGEON_CHEST_ENTITY::get);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DungeonChestBlockEntity chest) {
            chest.open(sp);
        }
        return InteractionResult.CONSUME;
    }
}
