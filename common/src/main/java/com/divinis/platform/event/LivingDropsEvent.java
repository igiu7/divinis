package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import java.util.Collection;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/** A dead creature drops these items (the collection can be changed; cancelled: nothing drops). */
public class LivingDropsEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;
    private final Collection<ItemEntity> drops;
    private final boolean recentlyHit;

    public LivingDropsEvent(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean recentlyHit) {
        this.entity = entity;
        this.source = source;
        this.drops = drops;
        this.recentlyHit = recentlyHit;
    }

    public LivingEntity getEntity() { return entity; }
    public DamageSource getSource() { return source; }
    public Collection<ItemEntity> getDrops() { return drops; }
    public boolean isRecentlyHit() { return recentlyHit; }
}
