package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingEvent extends Event {
    private final LivingEntity entity;

    protected LivingEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() { return entity; }

    /** How well a creature can be seen by another (for mobs looking for targets): a multiplier of the range. */
    public static class LivingVisibilityEvent extends LivingEvent {
        private final Entity lookingEntity;
        private double visibilityModifier;

        public LivingVisibilityEvent(LivingEntity entity, @Nullable Entity lookingEntity, double modifier) {
            super(entity);
            this.lookingEntity = lookingEntity;
            this.visibilityModifier = modifier;
        }

        public void modifyVisibility(double mod) { visibilityModifier *= mod; }
        public double getVisibilityModifier() { return visibilityModifier; }
        @Nullable
        public Entity getLookingEntity() { return lookingEntity; }
    }
}
