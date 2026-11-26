package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** An entity is added to a level, on either side (cancelled: it is not). */
public class EntityJoinLevelEvent extends Event implements ICancellableEvent {
    private final Entity entity;
    private final Level level;

    public EntityJoinLevelEvent(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }

    public Entity getEntity() { return entity; }
    public Level getLevel() { return level; }
}
