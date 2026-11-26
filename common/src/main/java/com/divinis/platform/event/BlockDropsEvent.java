package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A broken block drops these items (the list can be changed; cancelled: nothing drops). */
public class BlockDropsEvent extends Event implements ICancellableEvent {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final BlockEntity blockEntity;
    private final List<ItemEntity> drops;
    private final Entity breaker;
    private final ItemStack tool;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
                           List<ItemEntity> drops, @Nullable Entity breaker, ItemStack tool) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.blockEntity = blockEntity;
        this.drops = drops;
        this.breaker = breaker;
        this.tool = tool;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    @Nullable
    public BlockEntity getBlockEntity() { return blockEntity; }
    public List<ItemEntity> getDrops() { return drops; }
    @Nullable
    public Entity getBreaker() { return breaker; }
    public ItemStack getTool() { return tool; }
}
