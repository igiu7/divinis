package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A spike of bone of Azramond's dance of bones (boss.Azramond): it bursts up out of the floor (UP ticks), stands, and
 * sinks back (DOWN ticks). Only seen - the warning circles under it do the hurting; client.AzramondRenderer.Props
 * raises and lowers it by height().
 */
public class LichSpike extends Entity {
    public static final int UP = 3, STAND = 16, DOWN = 5;

    public LichSpike(EntityType<? extends LichSpike> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** How far it is out of the floor (0..1). */
    public float height(float partialTick) {
        float t = tickCount + partialTick;
        if (t < UP) return t / UP;
        if (t < UP + STAND) return 1;
        return Math.max(0, 1 - (t - UP - STAND) / DOWN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > UP + STAND + DOWN) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
