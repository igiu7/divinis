package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/** One of Aurelion's two dead hands: it can be hit (by a blade, an arrow, anything) - Aurelion decides what that does. */
public class AurelionHand extends PartEntity<Aurelion> {
    final int index;
    private static final EntityDimensions SIZE = EntityDimensions.scalable(2.4F, 2.0F);

    AurelionHand(Aurelion boss, int index) {
        super(boss);
        this.index = index;
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return getParent().state() == Aurelion.AWAKE;
    }

    @Override
    public ItemStack getPickResult() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtHand(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return SIZE;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
