package com.divinis.boss;

import net.minecraft.core.particles.ParticleTypes;
import com.divinis.registry.ModParticles;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An egg sac the Arachnid flings (boss.Arachnid's brood): it flies in an arc onto its marked circle, sits there
 * breathing and HATCH ticks after landing bursts into BROOD cave spiders - unless it is broken first (two blows).
 * It shakes for its last two seconds.
 */
public class ArachnidEgg extends Entity {
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(ArachnidEgg.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHAKING = SynchedEntityData.defineId(ArachnidEgg.class, EntityDataSerializers.BOOLEAN);
    private static final int HATCH = 120, SHAKE = 40, BROOD = 2, BLOWS = 2;

    public final AnimationState pulseAnimation = new AnimationState();
    public final AnimationState hatchAnimation = new AnimationState();
    private Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
    private int flight = 1, age, blows;

    public ArachnidEgg(EntityType<? extends ArachnidEgg> type, Level level) {
        super(type, level);
    }

    /** Sets it flying from a point to where it lands, in flight ticks. */
    public void launch(Vec3 from, Vec3 to, int flight, Arachnid boss) {
        this.from = from;
        this.to = to;
        this.flight = Math.max(1, flight);
        moveTo(from.x, from.y, from.z, random.nextFloat() * 360, 0);
    }

    public boolean landed() {
        return entityData.get(LANDED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LANDED, false);
        builder.define(SHAKING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!landed() && tickCount > 1) level().addParticle(ModParticles.SILK.get(), getX(), getY() + 0.4, getZ(), 0, 0, 0);
            if (entityData.get(SHAKING) && random.nextInt(3) == 0) {
                level().addParticle(ModParticles.VENOM.get(), getX(), getY() + 0.6, getZ(), 0, 0.02, 0);
            }
            if (entityData.get(SHAKING)) {
                pulseAnimation.stop();
                hatchAnimation.startIfStopped(tickCount);
            } else {
                pulseAnimation.startIfStopped(tickCount);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        age++;
        if (!landed()) {
            float k = Math.min(1, age / (float) flight);
            Vec3 p = from.lerp(to, k).add(0, Math.sin(k * Math.PI) * 4.0, 0);
            setPos(p.x, p.y, p.z);
            if (k >= 1) {
                entityData.set(LANDED, true);
                age = 0;
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
                Arachnid.silk(level, position().add(0, 0.4, 0), 12, 0.4);
            }
            return;
        }
        if (age == HATCH - SHAKE) entityData.set(SHAKING, true);
        if (age > HATCH - SHAKE && age % 8 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        if (age >= HATCH) {
            for (int i = 0; i < BROOD; i++) {
                CaveSpider s = EntityType.CAVE_SPIDER.create(level);
                if (s == null) continue;
                s.moveTo(getX() + (random.nextDouble() - 0.5), getY(), getZ() + (random.nextDouble() - 0.5), random.nextFloat() * 360, 0);
                s.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                s.addTag(Arachnid.BROOD_TAG);
                level.addFreshEntity(s);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_HATCH, SoundSource.HOSTILE, 2.0F, 0.7F);
            burst(level);
            discard();
        }
    }

    private void burst(ServerLevel level) {
        level.sendParticles(ModParticles.SILK.get(), getX(), getY() + 0.5, getZ(), 16, 0.4, 0.4, 0.4, 0.04);
        level.sendParticles(ModParticles.ACID_SPLASH.get(), getX(), getY() + 0.1, getZ(), 3, 0.3, 0.0, 0.3, 0);
        level.sendParticles(ModParticles.VENOM.get(), getX(), getY() + 0.4, getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.4, getZ(), 8, 0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !landed() || !(source.getEntity() instanceof Player)) return false;
        blows++;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_HURT_SMALL, SoundSource.HOSTILE, 1.2F, 0.8F);
        if (blows >= BLOWS) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
            burst((ServerLevel) level());
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved() && landed();
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
