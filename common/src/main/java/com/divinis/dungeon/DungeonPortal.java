package com.divinis.dungeon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A round portal, 3 blocks tall. The entrance one stands where it was opened for 30 seconds and takes whoever walks
 * into it to the dungeon; the exit one appears in the dungeon once the boss is dead and takes the party home.
 * It is never saved: after a restart the runs are over anyway (DungeonService).
 */
public class DungeonPortal extends Entity {
    /** ticks until it closes, -1 = it stays */
    private static final EntityDataAccessor<Integer> REMAINING = SynchedEntityData.defineId(DungeonPortal.class, EntityDataSerializers.INT);
    public static final int GROW_TICKS = 15;

    public final AnimationState swirlAnimation = new AnimationState();

    /** server: the run it leads into or out of, and which way */
    int instanceId = -1;
    boolean exit;

    public DungeonPortal(EntityType<? extends DungeonPortal> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(REMAINING, -1);
    }

    public int remaining() {
        return entityData.get(REMAINING);
    }

    void setRemaining(int ticks) {
        entityData.set(REMAINING, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swirlAnimation.startIfStopped(tickCount);
            // sparks drawn into the ring, which stands across the way the portal faces
            double rx = Math.cos(getYRot() * Math.PI / 180), rz = Math.sin(getYRot() * Math.PI / 180);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, c = Math.cos(a) * 1.4, s = Math.sin(a) * 1.4;
                level().addParticle(ParticleTypes.PORTAL, getX() + rx * c, getY() + 1.5 + s, getZ() + rz * c,
                        -rx * c * 0.3, -s * 0.3, -rz * c * 0.3);
            }
            return;
        }
        int left = remaining();
        if (left > 0) {
            setRemaining(--left);
            if (left == 0) {
                discard();
                DungeonService.portalClosed(this);
                return;
            }
        }
        if (instanceId < 0) {
            discard(); // a portal left over from before a restart
            return;
        }
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().deflate(0.3, 0, 0.3))) {
            DungeonService.touched(player, this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
