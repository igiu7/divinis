package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** A player hits something: whether it was a critical hit. */
public class CriticalHitEvent extends Event {
    private final Player player;
    private final Entity target;
    private final boolean critical;

    public CriticalHitEvent(Player player, Entity target, boolean critical) {
        this.player = player;
        this.target = target;
        this.critical = critical;
    }

    public Player getEntity() { return player; }
    public Entity getTarget() { return target; }
    public boolean isCriticalHit() { return critical; }
}
