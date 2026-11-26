package com.divinis.boss;

import com.divinis.registry.ModParticles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A ghost of the land of the dead (boss.Azramond): it drifts towards the nearest player, slower than one walks; a
 * touch hurts (and it is gone); it fades in the circles of light, at a blow, and when the land of the dead is over.
 */
public class LichGhost extends Entity {
    private static final double SPEED = 0.09;
    private static final float DAMAGE = 10;

    public final AnimationState floatAnimation = new AnimationState();
    private int bossId = -1, life = 160;

    public LichGhost(EntityType<? extends LichGhost> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    void owner(Azramond boss, int life) {
        this.bossId = boss.getId();
        this.life = life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            floatAnimation.startIfStopped(tickCount);
            if (tickCount % 3 == 0) level().addParticle(ModParticles.NECRO_FLAME.get(), getX(), getY() + 0.3, getZ(), 0, -0.01, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Azramond boss = level.getEntity(bossId) instanceof Azramond a ? a : null;
        if (boss == null || !boss.isAlive() || tickCount > life) {
            fade(level);
            return;
        }
        if (boss.inLight(position())) {
            level.sendParticles(ModParticles.HOLY_LIGHT.get(), getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.03);
            fade(level);
            return;
        }
        Player near = null;
        double best = 40 * 40;
        for (Player p : boss.fighters()) {
            double d = p.distanceToSqr(this);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near == null) return;
        Vec3 to = near.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() > 1e-4) {
            Vec3 step = flat.normalize().scale(SPEED);
            setPos(getX() + step.x, getY() + Mth.clamp(to.y, -0.05, 0.05), getZ() + step.z);
            setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        }
        if (getBoundingBox().inflate(0.15).intersects(near.getBoundingBox())) { // its touch
            if (near.hurt(damageSources().mobAttack(boss), DAMAGE * boss.damageScale())) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            fade(level);
        }
    }

    private void fade(ServerLevel level) {
        level.sendParticles(ModParticles.NECRO_FLAME.get(), getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.03);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 3, 0.2, 0.3, 0.2, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player)) return false;
        fade((ServerLevel) level());
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
