package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A creature is about to be hurt (the amount can be changed; cancelled: it is not hurt). */
public class LivingIncomingDamageEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }

    public LivingEntity getEntity() { return entity; }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
}
