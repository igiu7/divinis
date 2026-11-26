package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import javax.annotation.Nullable;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Two animals are about to have a baby (cancelled: none is born). */
public class BabyEntitySpawnEvent extends Event implements ICancellableEvent {
    private final Mob parentA;
    private final Mob parentB;
    private final Player causedBy;
    private AgeableMob child;

    public BabyEntitySpawnEvent(Mob parentA, Mob parentB, @Nullable AgeableMob child, @Nullable Player causedBy) {
        this.parentA = parentA;
        this.parentB = parentB;
        this.child = child;
        this.causedBy = causedBy;
    }

    public Mob getParentA() { return parentA; }
    public Mob getParentB() { return parentB; }
    @Nullable
    public Player getCausedByPlayer() { return causedBy; }
    @Nullable
    public AgeableMob getChild() { return child; }
    public void setChild(@Nullable AgeableMob child) { this.child = child; }
}
