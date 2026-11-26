package com.divinis.boss;

import com.divinis.registry.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A heap of bones Azramond raises (boss.Azramond): RISE ticks after it appears it gets up as a skeleton - unless it is
 * broken first (BLOWS blows). It rattles for its last second.
 */
public class LichBones extends Entity {
    private static final EntityDataAccessor<Boolean> RATTLING = SynchedEntityData.defineId(LichBones.class, EntityDataSerializers.BOOLEAN);
    public static final int RISE = 60;
    private static final int BLOWS = 2;

    public final AnimationState rattleAnimation = new AnimationState();
    private int age, blows;

    public LichBones(EntityType<? extends LichBones> type, Level level) {
        super(type, level);
    }

    void owner(Azramond boss) {}

    public boolean rattling() {
        return entityData.get(RATTLING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RATTLING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (rattling()) rattleAnimation.startIfStopped(tickCount);
            if (tickCount % 6 == 0) level().addParticle(ModParticles.NECRO_FLAME.get(), getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        age++;
        if (age == RISE - 20) entityData.set(RATTLING, true);
        if (age > RISE - 20 && age % 5 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.0F, 1.4F);
        if (age >= RISE) {
            Skeleton s = EntityType.SKELETON.create(level);
            if (s != null) {
                s.moveTo(getX(), getY(), getZ(), getYRot(), 0);
                s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(position())), MobSpawnType.MOB_SUMMONED, null);
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                for (EquipmentSlot slot : EquipmentSlot.values()) s.setDropChance(slot, 0);
                s.addTag(Azramond.RAISED_TAG);
                level.addFreshEntity(s);
            }
            level.sendParticles(ModParticles.BONE_DUST.get(), getX(), getY() + 0.5, getZ(), 20, 0.4, 0.5, 0.4, 0.06);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), getX(), getY() + 0.4, getZ(), 12, 0.3, 0.4, 0.3, 0.04);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F);
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player)) return false;
        blows++;
        ServerLevel level = (ServerLevel) level();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_HIT, SoundSource.HOSTILE, 1.5F, 0.8F);
        level.sendParticles(ModParticles.BONE_DUST.get(), getX(), getY() + 0.4, getZ(), 6, 0.3, 0.2, 0.3, 0.05);
        if (blows >= BLOWS) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            level.sendParticles(ModParticles.BONE_DUST.get(), getX(), getY() + 0.4, getZ(), 24, 0.4, 0.3, 0.4, 0.08);
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
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
