package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public abstract class EntityTeleportEvent extends Event implements ICancellableEvent {
    private final Entity entity;

    protected EntityTeleportEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() { return entity; }

    /** A thrown ender pearl is about to teleport its player (cancelled: it does not). */
    public static class EnderPearl extends EntityTeleportEvent {
        public EnderPearl(Player player) {
            super(player);
        }

        @Override
        public Player getEntity() { return (Player) super.getEntity(); }
    }

    /** A chorus fruit is about to teleport who ate it (cancelled: it does not). */
    public static class ChorusFruit extends EntityTeleportEvent {
        public ChorusFruit(LivingEntity entity) {
            super(entity);
        }

        @Override
        public LivingEntity getEntity() { return (LivingEntity) super.getEntity(); }
    }
}
