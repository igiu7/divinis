package com.divinis.skill;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Wanderer's Camp (SkillEffects): a campfire with a pot, a bedroll and a log to sit on (tools/models/
 * build_skill_camp.py, drawn by client.SkillCampRenderer) that stands LIFE ticks. Everyone within RADIUS blocks of its
 * fire slowly heals (Regeneration) and keeps warm (climate.Climate asks near). One camp a player; never saved.
 */
public class SkillCamp extends Entity {
    public static final int LIFE = 60 * 20, RADIUS = 5;

    private static final EntityDataAccessor<Long> END = SynchedEntityData.defineId(SkillCamp.class, EntityDataSerializers.LONG);

    UUID owner;

    public SkillCamp(EntityType<? extends SkillCamp> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, 0L);
    }

    /** The game time it goes out. */
    public long end() {
        return entityData.get(END);
    }

    void light(UUID owner) {
        this.owner = owner;
        entityData.set(END, level().getGameTime() + LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 0.3,
                        getZ() + 0.125 + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
            }
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.2, getZ() + 0.125, 0, 0.04, 0);
            }
            if (random.nextInt(10) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.6,
                        getZ() + 0.125, 0, 0.05, 0);
            }
            return;
        }
        if (level().getGameTime() >= end()) {
            discard();
            return;
        }
        if (tickCount % 40 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F);
            for (ServerPlayer p : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(RADIUS, 2, RADIUS))) {
                MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
                if (regen == null || regen.getDuration() < 50) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, true));
            }
        }
        if (!level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ())).isSolid()) discard(); // its ground is gone
    }

    /** Whether a camp's fire is near enough to warm this place. */
    public static boolean near(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(SkillCamp.class, new AABB(pos).inflate(RADIUS, 2, RADIUS)).isEmpty();
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
