package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/** A mob was spawned and is being set up (its gear, its variant). */
public class FinalizeSpawnEvent extends Event {
    private final Mob mob;
    private final ServerLevelAccessor level;
    private final MobSpawnType spawnType;

    public FinalizeSpawnEvent(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        this.mob = mob;
        this.level = level;
        this.spawnType = spawnType;
    }

    public Mob getEntity() { return mob; }
    public ServerLevelAccessor getLevel() { return level; }
    public MobSpawnType getSpawnType() { return spawnType; }
}
