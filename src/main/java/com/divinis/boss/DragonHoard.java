package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Ignaroth's hoard (boss.Ignaroth): heaps of gold in the middle of his crater; he sleeps curled on it. Only seen
 * (client.IgnarothRenderer.Props); he puts it there again after a restart.
 */
public class DragonHoard extends Entity {
    public DragonHoard(EntityType<? extends DragonHoard> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
