package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A shield is about to block a hit: whether it does, and how much of the damage it takes away. */
public class LivingShieldBlockEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float originalBlocked;
    private float blockedDamage;
    private boolean blocked;

    public LivingShieldBlockEvent(LivingEntity entity, DamageSource source, float originalBlocked, boolean blocked) {
        this.entity = entity;
        this.source = source;
        this.originalBlocked = originalBlocked;
        this.blockedDamage = originalBlocked;
        this.blocked = blocked;
    }

    public LivingEntity getEntity() { return entity; }
    public DamageSource getDamageSource() { return source; }
    public float getOriginalBlockedDamage() { return originalBlocked; }
    public float getBlockedDamage() { return blockedDamage; }
    public void setBlockedDamage(float damage) { this.blockedDamage = damage; }
    public boolean getBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
}
