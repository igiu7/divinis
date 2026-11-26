package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

public abstract class ExplosionEvent extends Event {
    private final Level level;
    private final Explosion explosion;

    protected ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() { return level; }
    public Explosion getExplosion() { return explosion; }

    /** An explosion is about to destroy these blocks and hit these entities (both lists can be changed). */
    public static class Detonate extends ExplosionEvent {
        private final List<Entity> entities;

        public Detonate(Level level, Explosion explosion, List<Entity> entities) {
            super(level, explosion);
            this.entities = entities;
        }

        public List<BlockPos> getAffectedBlocks() { return getExplosion().getToBlow(); }
        public List<Entity> getAffectedEntities() { return entities; }
    }
}
