package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;

/**
 * The skin the Arachnid sheds at half health (boss.Arachnid): it falls from the thread to the floor, lies there on its
 * back, legs curled, pale and see-through (client.ArachnidMoltRenderer), and fades away after LIFE ticks. Only seen.
 */
public class ArachnidMolt extends Entity {
    public static final int LIFE = 600, FADE = 60;

    public ArachnidMolt(EntityType<? extends ArachnidMolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.06, 0).scale(0.98));
        else setDeltaMovement(0, 0, 0);
        move(MoverType.SELF, getDeltaMovement());
        if (!level().isClientSide && tickCount >= LIFE) discard();
    }

    /** Client: how much of it is left to see (1 .. 0 in its last FADE ticks). */
    public float fade(float partialTick) {
        return Math.max(0, Math.min(1, (LIFE - tickCount - partialTick) / FADE));
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
