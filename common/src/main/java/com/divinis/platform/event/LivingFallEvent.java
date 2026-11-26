package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** A creature lands (distance and damage multiplier can be changed; cancelled: no fall damage). */
public class LivingFallEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private float distance;
    private float damageMultiplier;

    public LivingFallEvent(LivingEntity entity, float distance, float damageMultiplier) {
        this.entity = entity;
        this.distance = distance;
        this.damageMultiplier = damageMultiplier;
    }

    public LivingEntity getEntity() { return entity; }
    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = distance; }
    public float getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(float multiplier) { this.damageMultiplier = multiplier; }
}
