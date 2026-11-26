package com.divinis.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.divinis.platform.entity.PartEntity;

/**
 * One of the Hydra's heads (boss.Hydra), or the stump where it was cut off: it can be hit (the head has its own health)
 * and, a bleeding stump, burned shut with a torch, flint and steel or a fire charge (used on it).
 */
public class HydraHead extends PartEntity<Hydra> {
    final int slot;
    private final EntityDimensions size;

    HydraHead(Hydra hydra, int slot, float size) {
        super(hydra);
        this.slot = slot;
        this.size = EntityDimensions.scalable(size, size * 0.85F);
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
        return getParent().struckAt(slot);
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtHead(slot, source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().burnWith(slot, player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
