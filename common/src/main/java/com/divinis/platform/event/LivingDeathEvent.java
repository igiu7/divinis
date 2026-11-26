package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A creature is about to die (cancelled: it lives on). */
public class LivingDeathEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }

    public LivingEntity getEntity() { return entity; }
    public DamageSource getSource() { return source; }
}
