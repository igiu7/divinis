package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Azramond's throne of bones (boss.Azramond): it stands where he was summoned, he sleeps on it, and he breaks it at half
 * his health. Only seen (client.AzramondRenderer.Props); he puts it there again after a restart.
 */
public class LichThrone extends Entity {
    public LichThrone(EntityType<? extends LichThrone> type, Level level) {
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
