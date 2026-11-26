package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** A creature is about to heal (the amount can be changed; cancelled: it does not). */
public class LivingHealEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private float amount;

    public LivingHealEvent(LivingEntity entity, float amount) {
        this.entity = entity;
        this.amount = amount;
    }

    public LivingEntity getEntity() { return entity; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
}
