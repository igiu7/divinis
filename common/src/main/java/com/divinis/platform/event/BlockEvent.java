package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }

    /** A player is about to break a block (cancelled: it is not broken). */
    public static class BreakEvent extends BlockEvent implements ICancellableEvent {
        private final Player player;

        public BreakEvent(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() { return player; }
    }

    /** An entity placed a block (cancelled: it is taken back). */
    public static class EntityPlaceEvent extends BlockEvent implements ICancellableEvent {
        private final Entity entity;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placed, @Nullable Entity entity) {
            super(level, pos, placed);
            this.entity = entity;
        }

        @Nullable
        public Entity getEntity() { return entity; }
        public BlockState getPlacedBlock() { return getState(); }
    }
}
